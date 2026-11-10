package ir.herotux.pregnancytracker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class BabyName(val id:String,val name:String,val gender:String,val meaning:String="",val origin:String="")
data class SavedBabyName(val id:String,val name:String,val gender:String,val meaning:String="",val origin:String="",val note:String="",val custom:Boolean=false)
data class BabyNameList(val id:String,val title:String,val items:List<SavedBabyName>)

private val starterNamesFallback=listOf(
 BabyName("g1","آوا","دختر","آواز و نوا","فارسی"),BabyName("g2","رها","دختر","آزاد و وارسته","فارسی"),
 BabyName("g3","آناهید","دختر","پاک و بی‌آلایش؛ نام ایزدبانوی آب","ایرانی"),BabyName("g4","باران","دختر","بارش باران","فارسی"),
 BabyName("g5","مانا","دختر","پایدار و ماندگار","فارسی"),BabyName("g6","دریا","دختر","دریا","فارسی"),
 BabyName("g7","یسنا","دختر","ستایش و نیایش","اوستایی"),BabyName("g8","مهرا","دختر","وابسته به مهر و محبت","فارسی"),
 BabyName("g9","آیرا","دختر"),BabyName("g10","نیلا","دختر"),BabyName("g11","هانا","دختر"),BabyName("g12","آترینا","دختر"),
 BabyName("b1","آراد","پسر"),BabyName("b2","آرتین","پسر"),BabyName("b3","رادین","پسر","جوانمرد و بخشنده","فارسی"),
 BabyName("b4","ماهان","پسر","منسوب به ماه","فارسی"),BabyName("b5","سام","پسر","نام پهلوان ایرانی","ایرانی"),
 BabyName("b6","آریا","پسر","نجیب و آزاده","ایرانی"),BabyName("b7","رایان","پسر"),BabyName("b8","هیراد","پسر"),
 BabyName("b9","بردیا","پسر","نام شاهزاده هخامنشی","ایرانی"),BabyName("b10","نیوان","پسر"),
 BabyName("b11","پارسا","پسر","پرهیزگار","فارسی"),BabyName("b12","آروین","پسر")
)


private fun readBabyNameBank(context:android.content.Context):List<BabyName> = runCatching {
 val json=JSONObject(context.assets.open("names_bank.json").bufferedReader().use{it.readText()})
 val array=json.getJSONArray("names")
 (0 until array.length()).map { i -> val n=array.getJSONObject(i);BabyName(n.optString("id"),n.optString("name"),n.optString("gender"),n.optString("meaning"),n.optString("origin")) }
}.getOrDefault(starterNamesFallback)

fun encodeBabyNameLists(lists:List<BabyNameList>):String {
 val root=JSONArray()
 lists.forEach { l -> val a=JSONArray();l.items.forEach { n -> a.put(JSONObject().put("id",n.id).put("name",n.name).put("gender",n.gender).put("meaning",n.meaning).put("origin",n.origin).put("note",n.note).put("custom",n.custom)) };root.put(JSONObject().put("id",l.id).put("title",l.title).put("items",a)) }
 return root.toString()
}
fun decodeBabyNameLists(raw:String?):List<BabyNameList> = runCatching {
 val root=JSONArray(raw ?: "[]")
 (0 until root.length()).map { i -> val o=root.getJSONObject(i);val a=o.optJSONArray("items")?:JSONArray()
  BabyNameList(o.optString("id",UUID.randomUUID().toString()),o.optString("title"),(0 until a.length()).map { j -> val n=a.getJSONObject(j);SavedBabyName(n.optString("id"),n.optString("name"),n.optString("gender","نامشخص"),n.optString("meaning"),n.optString("origin"),n.optString("note"),n.optBoolean("custom")) })
 }
}.getOrDefault(emptyList())

@Composable
fun BabyNamesScreen(savedLists:List<BabyNameList>,onSave:(List<BabyNameList>)->Unit,pad:PaddingValues) {
 val context=LocalContext.current
 val bankNames=remember { readBabyNameBank(context) }
 var moveNameId by remember { mutableStateOf<String?>(null) }
 var tab by remember { mutableIntStateOf(0) };var gender by remember { mutableStateOf("دختر") };var query by remember { mutableStateOf("") }
 var activeId by remember { mutableStateOf<String?>(null) };var listTitle by remember { mutableStateOf("") };var createDialog by remember { mutableStateOf(false) }
 var customName by remember { mutableStateOf("") };var customGender by remember { mutableStateOf("دختر") };var customDialog by remember { mutableStateOf(false) }
 var noteId by remember { mutableStateOf<String?>(null) };var noteText by remember { mutableStateOf("") };var addName by remember { mutableStateOf<BabyName?>(null) }
 var targetList by remember { mutableStateOf("") }
 val active=savedLists.firstOrNull{it.id==activeId}
 fun update(l:BabyNameList){onSave(savedLists.map{if(it.id==l.id)l else it})}
 fun append(listId:String,n:BabyName,custom:Boolean=false){val l=savedLists.firstOrNull{it.id==listId}?:return;if(l.items.any{it.name.equals(n.name,true)})return;update(l.copy(items=l.items+SavedBabyName(UUID.randomUUID().toString(),n.name,n.gender,n.meaning,n.origin,custom=custom)))}
 Column(Modifier.fillMaxSize().padding(pad).padding(horizontal=16.dp)) {
  Text("بانک اسامی",fontFamily=Fa,fontSize=25.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=8.dp))
  Text("اسم‌ها را پیدا کن، در فهرست‌های دلخواهت نگه دار و برایشان یادداشت بنویس.",fontFamily=Fa,fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=4.dp,bottom=10.dp))
  ScrollableTabRow(selectedTabIndex=tab,edgePadding=0.dp){Tab(tab==0,{tab=0;activeId=null},{Text("بانک نام‌ها",fontFamily=Fa)});Tab(tab==1,{tab=1;activeId=null},{Text("فهرست‌های من",fontFamily=Fa)})}
  if(tab==0){
   Row(Modifier.fillMaxWidth().padding(top=10.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(gender=="دختر",{gender="دختر"},{Text("دخترانه",fontFamily=Fa)},leadingIcon={Icon(Icons.Default.Favorite,null)});FilterChip(gender=="پسر",{gender="پسر"},{Text("پسرانه",fontFamily=Fa)},leadingIcon={Icon(Icons.Default.Person,null)})}
   OutlinedTextField(query,{query=it},Modifier.fillMaxWidth().padding(top=7.dp),label={Text("جست‌وجوی اسم",fontFamily=Fa)},singleLine=true,shape=RoundedCornerShape(14.dp))
   if(savedLists.isNotEmpty()){
    var expanded by remember{mutableStateOf(false)}
    ExposedDropdownMenuBox(expanded,{expanded=it},Modifier.fillMaxWidth().padding(top=6.dp)){
     OutlinedTextField(value=savedLists.firstOrNull{it.id==targetList}?.title?:"انتخاب فهرست مقصد",onValueChange={},readOnly=true,modifier=Modifier.menuAnchor().fillMaxWidth(),label={Text("افزودن اسم به فهرست",fontFamily=Fa)},trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(expanded)})
     ExposedDropdownMenu(expanded,{expanded=false}){savedLists.forEach{l->DropdownMenuItem(text={Text(l.title,fontFamily=Fa)},onClick={targetList=l.id;expanded=false})}}
    }
   }else Text("برای ذخیره اسم‌ها، ابتدا در «فهرست‌های من» یک فهرست بساز.",fontFamily=Fa,fontSize=12.sp,color=RoseDark,modifier=Modifier.padding(10.dp))
   LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
    items(bankNames.filter{it.gender==gender&&(query.isBlank()||it.name.contains(query,true)||it.meaning.contains(query,true))},key={it.id}){n->
     Card(shape=RoundedCornerShape(16.dp)){Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically){
      Column(Modifier.weight(1f)){Text(n.name,fontFamily=Fa,fontWeight=FontWeight.Bold,fontSize=16.sp);if(n.meaning.isNotBlank())Text(n.meaning+" • "+n.origin,fontFamily=Fa,fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) else Text("معنی و ریشه هنوز بررسی نشده",fontFamily=Fa,fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
      IconButton(onClick={if(targetList.isNotBlank())append(targetList,n)else addName=n},enabled=savedLists.isNotEmpty()){Icon(Icons.Default.Add,"افزودن به فهرست",tint=RoseDark)}
     }}
    }
   }
  }else if(active==null){
   Row(Modifier.fillMaxWidth().padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically){Text("فهرست‌های شخصی",fontFamily=Fa,fontWeight=FontWeight.Bold,fontSize=17.sp,modifier=Modifier.weight(1f));Button(onClick={createDialog=true},shape=RoundedCornerShape(14.dp)){Icon(Icons.Default.Add,null);Text("فهرست جدید",fontFamily=Fa)}}
   if(savedLists.isEmpty())Column(Modifier.fillMaxWidth().padding(top=30.dp),horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Default.Bookmark,null,tint=RoseDark,modifier=Modifier.size(40.dp));Text("هنوز فهرستی نساخته‌ای",fontFamily=Fa,fontWeight=FontWeight.Bold);Text("مثل «اسم‌های محبوب» یا «انتخاب نهایی».",fontFamily=Fa,fontSize=12.sp)}
   else LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){items(savedLists,key={it.id}){l->Card(Modifier.fillMaxWidth().clickable{activeId=l.id},shape=RoundedCornerShape(17.dp)){Row(Modifier.fillMaxWidth().padding(15.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Bookmark,null,tint=RoseDark);Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text(l.title,fontFamily=Fa,fontWeight=FontWeight.Bold);Text(fa(l.items.size)+" اسم",fontFamily=Fa,fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)};Icon(Icons.Default.ChevronLeft,null)}}}}
  }else{
   Row(Modifier.fillMaxWidth().padding(vertical=7.dp),verticalAlignment=Alignment.CenterVertically){IconButton(onClick={activeId=null}){Icon(Icons.Default.ArrowBack,"بازگشت")};Column(Modifier.weight(1f)){Text(active.title,fontFamily=Fa,fontWeight=FontWeight.Bold,fontSize=17.sp);Text(fa(active.items.size)+" اسم",fontFamily=Fa,fontSize=11.sp)};IconButton(onClick={customDialog=true}){Icon(Icons.Default.Add,"افزودن اسم دلخواه")}}
   if(active.items.isEmpty())Text("این فهرست خالی است؛ از بانک اسم انتخاب کن یا اسم دلخواهت را اضافه کن.",fontFamily=Fa,fontSize=13.sp,modifier=Modifier.padding(top=14.dp))
   LazyColumn(verticalArrangement=Arrangement.spacedBy(7.dp),contentPadding=PaddingValues(bottom=16.dp)){
    items(active.items,key={it.id}){n->Card(shape=RoundedCornerShape(15.dp)){Column(Modifier.fillMaxWidth().padding(10.dp)){
     Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(n.name,fontFamily=Fa,fontWeight=FontWeight.Bold,fontSize=16.sp);Text(n.gender+(if(n.custom)" • شخصی" else ""),fontFamily=Fa,fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
      val index=active.items.indexOfFirst{it.id==n.id}
      IconButton(enabled=index>0,onClick={val x=active.items.toMutableList();val v=x.removeAt(index);x.add(index-1,v);update(active.copy(items=x))}){Icon(Icons.Default.ArrowUpward,"بالا")}
      IconButton(enabled=index<active.items.lastIndex,onClick={val x=active.items.toMutableList();val v=x.removeAt(index);x.add(index+1,v);update(active.copy(items=x))}){Icon(Icons.Default.ArrowDownward,"پایین")}
      IconButton(onClick={noteId=n.id;noteText=n.note}){Icon(Icons.Default.EditNote,"یادداشت")}
      if(savedLists.size>1) IconButton(onClick={moveNameId=n.id}){Icon(Icons.Default.SwapHoriz,"انتقال به فهرست دیگر")}
      IconButton(onClick={update(active.copy(items=active.items.filterNot{it.id==n.id}))}){Icon(Icons.Default.DeleteOutline,"حذف",tint=MaterialTheme.colorScheme.error)}
     }
     if(n.meaning.isNotBlank())Text(n.meaning,fontFamily=Fa,fontSize=11.sp)
     if(n.note.isNotBlank())Text("یادداشت: "+n.note,fontFamily=Fa,fontSize=12.sp,color=RoseDark,modifier=Modifier.padding(top=4.dp))
    }}}
   }
  }
 }
 if(createDialog)AlertDialog(onDismissRequest={createDialog=false},title={Text("ساخت فهرست",fontFamily=Fa)},text={OutlinedTextField(listTitle,{listTitle=it},label={Text("نام فهرست",fontFamily=Fa)},singleLine=true)},confirmButton={TextButton(onClick={if(listTitle.isNotBlank()){onSave(savedLists+BabyNameList(UUID.randomUUID().toString(),listTitle.trim(),emptyList()));listTitle="";createDialog=false}}){Text("ساختن",fontFamily=Fa)}},dismissButton={TextButton(onClick={createDialog=false}){Text("انصراف",fontFamily=Fa)}})
 if(customDialog&&active!=null)AlertDialog(onDismissRequest={customDialog=false},title={Text("افزودن اسم دلخواه",fontFamily=Fa)},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(customName,{customName=it},label={Text("نام",fontFamily=Fa)},singleLine=true);Row{FilterChip(customGender=="دختر",{customGender="دختر"},{Text("دختر",fontFamily=Fa)});Spacer(Modifier.width(8.dp));FilterChip(customGender=="پسر",{customGender="پسر"},{Text("پسر",fontFamily=Fa)})}}},confirmButton={TextButton(onClick={if(customName.isNotBlank()){append(active.id,BabyName(UUID.randomUUID().toString(),customName.trim(),customGender),true);customName="";customDialog=false}}){Text("افزودن",fontFamily=Fa)}},dismissButton={TextButton(onClick={customDialog=false}){Text("انصراف",fontFamily=Fa)}})
 if(addName!=null)AlertDialog(onDismissRequest={addName=null},title={Text("انتخاب فهرست",fontFamily=Fa)},text={Column{Text("اسم «${addName!!.name}» به کدام فهرست اضافه شود؟",fontFamily=Fa);savedLists.forEach{l->TextButton(onClick={append(l.id,addName!!);addName=null}){Text(l.title,fontFamily=Fa)}}}},confirmButton={TextButton(onClick={addName=null}){Text("بستن",fontFamily=Fa)}})
 if(moveNameId!=null&&active!=null)AlertDialog(onDismissRequest={moveNameId=null},title={Text("انتقال اسم به فهرست دیگر",fontFamily=Fa)},text={Column{Text("اسم انتخاب‌شده از این فهرست برداشته و به مقصد منتقل می‌شود.",fontFamily=Fa,fontSize=12.sp);savedLists.filter{it.id!=active.id}.forEach{dest->TextButton(onClick={val n=active.items.firstOrNull{it.id==moveNameId};if(n!=null){val exists=dest.items.any{it.name.equals(n.name,true)};if(!exists)update(dest.copy(items=dest.items+n));update(active.copy(items=active.items.filterNot{it.id==moveNameId}))};moveNameId=null}){Text(dest.title,fontFamily=Fa)}}}},confirmButton={TextButton(onClick={moveNameId=null}){Text("انصراف",fontFamily=Fa)}})
 if(noteId!=null)AlertDialog(onDismissRequest={noteId=null},title={Text("یادداشت اسم",fontFamily=Fa)},text={OutlinedTextField(noteText,{noteText=it},label={Text("یادداشت شخصی",fontFamily=Fa)},minLines=3)},confirmButton={TextButton(onClick={val l=savedLists.firstOrNull{it.id==activeId};if(l!=null)update(l.copy(items=l.items.map{if(it.id==noteId)it.copy(note=noteText)else it}));noteId=null}){Text("ذخیره",fontFamily=Fa)}},dismissButton={TextButton(onClick={noteId=null}){Text("انصراف",fontFamily=Fa)}})
}
