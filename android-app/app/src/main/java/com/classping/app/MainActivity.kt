package com.classping.app

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.classping.app.data.*
import com.classping.app.notifications.ReminderScheduler
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.*
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { ClassPingApp { if (android.os.Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS) } } }
}

class ClassPingViewModel(private val app: android.app.Application) : AndroidViewModel(app) {
    private val store = PreferencesStore(app); private val repo = ClassPingRepository()
    private val _state = MutableStateFlow(AppState()); val state = _state.asStateFlow()
    init { viewModelScope.launch { store.preferences.collectLatest { p -> _state.update { it.copy(user=p) }; if (!p.onboardingComplete) return@collectLatest; runCatching { repo.ensureAnonymousUser() }; observe(p) } } }
    private suspend fun observe(p: UserPreferences) {
        combine(repo.observeEvents(p), repo.observeConfig()) { e, c -> e to c }.catch { _state.update { it.copy(loading=false, error="We’ll keep showing your saved schedule while we reconnect.") } }.collectLatest { (events, config) ->
            _state.update { it.copy(loading=false, events=events.first, offline=events.second, config=config) }; ReminderScheduler(app).reconcile(events.first, p)
        }
    }
    fun save(p: UserPreferences) = viewModelScope.launch { store.save(p); runCatching { repo.saveProfile(p); repo.saveFcmToken(app) } }
    fun reset() = viewModelScope.launch { store.clear() }
}

@Composable fun ClassPingApp(onPermission: () -> Unit, vm: ClassPingViewModel = viewModel()) {
    val state by vm.state.collectAsState(); val nav = rememberNavController()
    ClassPingTheme(state.user.theme) { if (!state.user.onboardingComplete) Onboarding(state.user, vm::save, onPermission) else MainShell(state, nav, vm) }
}

@Composable private fun Onboarding(initial: UserPreferences, save: (UserPreferences)->Unit, permission: ()->Unit) {
    var page by remember { mutableIntStateOf(0) }; var p by remember { mutableStateOf(initial) }
    val next = { if (page < 3) page++ else { save(p.copy(onboardingComplete=true)); permission() } }
    Surface(Modifier.fillMaxSize()) { Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement=Arrangement.SpaceBetween) {
        Column { Text("ClassPing", fontSize=36.sp, fontWeight=FontWeight.Bold, color=MaterialTheme.colorScheme.primary); Text("Know it before you miss it.", color=MaterialTheme.colorScheme.onSurfaceVariant, fontSize=16.sp); Spacer(Modifier.height(56.dp));
            AnimatedContent(page, label="onboarding") { step -> when(step) {
                0 -> Welcome(); 1 -> NameStep(p.name) { p=p.copy(name=it) }; 2 -> ClassStep(p) { n,s -> p=p.copy(className=n, section=s) }; else -> VibeStep(p.theme) { p=p.copy(theme=it) }
            } }
        }
        Button(next, Modifier.fillMaxWidth().height(54.dp), shape=RoundedCornerShape(18.dp)) { Text(if(page==3) "Enable reminders" else if(page==0) "Get started" else "Continue") }
    } }
}
@Composable private fun Welcome() { Text("Tests, submissions and school reminders — automatically organized for you.", fontSize=23.sp, lineHeight=31.sp, fontWeight=FontWeight.SemiBold) }
@Composable private fun NameStep(value:String, onChange:(String)->Unit) { StepTitle("What should we call you?", "This name will appear in your reminders."); OutlinedTextField(value,onChange,label={Text("Your name")},placeholder={Text("Kanish")},singleLine=true,modifier=Modifier.fillMaxWidth()) }
@Composable private fun ClassStep(p:UserPreferences, onChange:(String,String)->Unit) { StepTitle("Your class and section", "You can change this later in Settings."); var n by remember(p.className){mutableStateOf(p.className)}; var s by remember(p.section){mutableStateOf(p.section)}; OutlinedTextField(n,{n=it;onChange(n,s)},label={Text("Class")},modifier=Modifier.fillMaxWidth()); Spacer(Modifier.height(12.dp)); OutlinedTextField(s,{s=it;onChange(n,s)},label={Text("Section")},modifier=Modifier.fillMaxWidth()) }
@Composable private fun VibeStep(value:ThemeMode,onChange:(ThemeMode)->Unit){ StepTitle("Choose your vibe", "You can follow the system later."); Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(12.dp)){ listOf(ThemeMode.LIGHT to "Light",ThemeMode.DARK to "Dark").forEach{(t,l)-> Card(Modifier.weight(1f).height(120.dp).clickable{onChange(t)},colors=CardDefaults.cardColors(containerColor=if(value==t)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text(l,fontWeight=FontWeight.Bold)}} } } }
@Composable private fun StepTitle(title:String, body:String){Text(title,fontSize=30.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(10.dp));Text(body,color=MaterialTheme.colorScheme.onSurfaceVariant)}

@Composable private fun MainShell(s:AppState, nav:NavHostController, vm:ClassPingViewModel) {
    val entry by nav.currentBackStackEntryAsState(); val route=entry?.destination?.route ?: "home"
    Scaffold(bottomBar={NavigationBar{listOf("home" to "Home" to Icons.Default.Home,"tests" to "Tests" to Icons.Default.Event,"submissions" to "Submissions" to Icons.Default.Inventory2,"settings" to "Settings" to Icons.Default.Settings).forEach{(pair,icon)->val(r,l)=pair;NavigationBarItem(route==r,{nav.navigate(r){popUpTo("home") {saveState=true};launchSingleTop=true;restoreState=true}}, {Icon(icon,l);Text(l)})}}}) { pad -> NavHost(nav,"home",Modifier.padding(pad)){ composable("home"){Home(s){nav.navigate("detail/${it.id}")}};composable("tests"){Tests(s){nav.navigate("detail/${it.id}")}};composable("submissions"){Submissions(s){nav.navigate("detail/${it.id}")}};composable("settings"){Settings(s.user,vm)};composable("detail/{id}"){ backStack->Detail(s.events.firstOrNull{it.id==backStack.arguments?.getString("id")},nav)}} }
}

@Composable private fun Header(s:AppState){ Text("Good ${when(java.time.LocalTime.now().hour){in 0..11->"morning";in 12..17->"afternoon";else->"evening"}}, ${s.user.name}",fontSize=27.sp,fontWeight=FontWeight.Bold);Text("Here’s what you need to know.",color=MaterialTheme.colorScheme.onSurfaceVariant) }
@Composable private fun Home(s:AppState,open:(SchoolEvent)->Unit){ val now=Instant.now();val upcoming=s.events.filter{it.eventAt?.isAfter(now)==true};val next=upcoming.firstOrNull();LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(16.dp),contentPadding=PaddingValues(top=24.dp,bottom=24.dp)){item{Header(s)};item{ if(next!=null) NextCard(next,open) else Empty("No upcoming events 🎉","Enjoy the peace while it lasts.")};item{Summary(s.events)};item{Text("UPCOMING",fontWeight=FontWeight.Bold,letterSpacing=1.4.sp)};items(upcoming.take(8),key={it.id}){EventRow(it,open)} }}
@Composable private fun NextCard(e:SchoolEvent,open:(SchoolEvent)->Unit){Card(Modifier.fillMaxWidth().clickable{open(e)},colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer),shape=RoundedCornerShape(24.dp)){Column(Modifier.padding(22.dp)){Text("NEXT UP",fontSize=12.sp,fontWeight=FontWeight.Bold,letterSpacing=1.5.sp,color=MaterialTheme.colorScheme.primary);Spacer(Modifier.height(10.dp));Text(e.title,fontSize=23.sp,fontWeight=FontWeight.Bold);Text(e.topic.ifBlank{e.subject},color=MaterialTheme.colorScheme.onSurfaceVariant);Spacer(Modifier.height(14.dp));Text(relative(e.eventAt),fontWeight=FontWeight.SemiBold);if(e.needsReview)Text("Date needs confirmation",color=MaterialTheme.colorScheme.error,fontSize=12.sp)}}}
@Composable private fun Summary(events:List<SchoolEvent>){val week=events.filter{it.eventAt?.isBefore(Instant.now().plus(7,java.time.temporal.ChronoUnit.DAYS))==true};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){listOf(EventType.TEST to "Tests",EventType.NOTEBOOK_SUBMISSION to "Submissions",EventType.ASSIGNMENT to "Assignments").forEach{(t,l)->Column{Text(week.count{it.type==t}.toString(),fontSize=25.sp,fontWeight=FontWeight.Bold);Text(l,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}}}}
@Composable private fun Tests(s:AppState,open:(SchoolEvent)->Unit){
    val upcomingTests = s.events
        .filter { it.type == EventType.TEST }
        .filter { isTodayOrFuture(it.eventAt) }
        .sortedWith(compareBy(nullsLast()) { it.eventAt })
    UpcomingOnlyListing("TESTS", upcomingTests, "No tests coming up 🎉", open)
}
@Composable private fun Submissions(s:AppState,open:(SchoolEvent)->Unit){EventListing("SUBMISSIONS",s.events.filter{it.type==EventType.NOTEBOOK_SUBMISSION},"No submissions due 🎉",open)}

@Composable private fun UpcomingOnlyListing(title:String,items:List<SchoolEvent>,empty:String,open:(SchoolEvent)->Unit){
    Column(Modifier.fillMaxSize().padding(horizontal=20.dp)){
        Spacer(Modifier.height(24.dp))
        Text(title,fontSize=27.sp,fontWeight=FontWeight.Bold)
        Text("Only today and upcoming tests are shown.",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=13.sp)
        Spacer(Modifier.height(14.dp))
        LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)){
            if(items.isEmpty()) item{Empty(empty,"Enjoy the peace while it lasts.")}
            items(items,key={it.id}){EventRow(it,open)}
        }
    }
}
@Composable private fun EventListing(title:String,items:List<SchoolEvent>,empty:String,open:(SchoolEvent)->Unit){var past by remember{mutableStateOf(false)};Column(Modifier.fillMaxSize().padding(horizontal=20.dp)){Spacer(Modifier.height(24.dp));Text(title,fontSize=27.sp,fontWeight=FontWeight.Bold);Row(Modifier.padding(vertical=14.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(!past,{past=false},{Text("Upcoming")});FilterChip(past,{past=true},{Text("Past")})};val visible=items.filter{(it.eventAt?.isAfter(Instant.now())==!past)};LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)){if(visible.isEmpty())item{Empty(empty,"Enjoy the peace while it lasts.")}items(visible,key={it.id}){EventRow(it,open)}}}}
@Composable private fun EventRow(e:SchoolEvent,open:(SchoolEvent)->Unit){Card(Modifier.fillMaxWidth().clickable{open(e)},shape=RoundedCornerShape(18.dp)){Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(e.subject,fontWeight=FontWeight.Bold);Text(e.topic.ifBlank{e.title},maxLines=1,overflow=TextOverflow.Ellipsis,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(e.dateLabel.ifBlank{e.eventAt?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("dd MMM"))?:"Date TBC")},fontSize=12.sp,color=MaterialTheme.colorScheme.primary)};Text(relative(e.eventAt),fontSize=12.sp,fontWeight=FontWeight.SemiBold)}}}
@Composable private fun Detail(e:SchoolEvent?,nav:NavHostController){if(e==null){Empty("Event unavailable","It may have been updated.");return};Column(Modifier.fillMaxSize().padding(20.dp)){IconButton({nav.popBackStack()}){Icon(Icons.Default.ArrowBack,"Back")};Spacer(Modifier.height(20.dp));Text(e.type.name.replace('_',' '),color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold);Text(e.title,fontSize=32.sp,fontWeight=FontWeight.Bold);Text(e.subject,fontSize=19.sp);Spacer(Modifier.height(28.dp));Info("When",e.dateLabel.ifBlank{relative(e.eventAt)});Info("Topic",e.topic.ifBlank{"No topic provided"});Info("Reminder","ClassPing will remind you before this event")}}
@Composable private fun Info(a:String,b:String){Text(a.uppercase(),fontSize=11.sp,letterSpacing=1.3.sp,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(b,fontSize=18.sp,fontWeight=FontWeight.Medium);Spacer(Modifier.height(20.dp))}
@Composable private fun Settings(p:UserPreferences,vm:ClassPingViewModel){var current by remember(p){mutableStateOf(p)};val scope=rememberCoroutineScope();LazyColumn(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(top=20.dp,bottom=24.dp)){item{Text("Settings",fontSize=32.sp,fontWeight=FontWeight.Bold)};item{OutlinedTextField(current.name,{current=current.copy(name=it);vm.save(current)},label={Text("Name")},modifier=Modifier.fillMaxWidth())};item{OutlinedTextField(current.className,{current=current.copy(className=it);vm.save(current)},label={Text("Class")},modifier=Modifier.fillMaxWidth())};item{OutlinedTextField(current.section,{current=current.copy(section=it);vm.save(current)},label={Text("Section")},modifier=Modifier.fillMaxWidth())};item{SettingToggle("Master notifications",current.notificationsEnabled){current=current.copy(notificationsEnabled=it);vm.save(current)}};item{SettingToggle("Test reminders",current.testAlerts){current=current.copy(testAlerts=it);vm.save(current)}};item{SettingToggle("Submission reminders",current.submissionAlerts){current=current.copy(submissionAlerts=it);vm.save(current)}};item{Text("Theme",fontWeight=FontWeight.Bold);SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()){ThemeMode.entries.forEach{t->SegmentedButton(current.theme==t,{current=current.copy(theme=t);vm.save(current)},shape=SegmentedButtonDefaults.itemShape(t.ordinal,ThemeMode.entries.size)){Text(t.name.lowercase().replaceFirstChar{it.uppercase()})}}}};item{Text("Reminder timing",fontWeight=FontWeight.Bold);Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf(12,24,48).forEach{h->FilterChip(current.reminderHours==h,{current=current.copy(reminderHours=h);vm.save(current)},{Text("$h hours")})}}};item{Text("Notification style",fontWeight=FontWeight.Bold);Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){ReminderTone.entries.forEach{t->FilterChip(current.tone==t,{current=current.copy(tone=t);vm.save(current)},{Text(t.name.lowercase().replaceFirstChar{it.uppercase()})})}}};item{OutlinedButton({vm.reset()},Modifier.fillMaxWidth()){Text("Reset onboarding")}};item{Text("ClassPing 1.0.0",color=MaterialTheme.colorScheme.onSurfaceVariant)}}}
@Composable private fun SettingToggle(label:String,value:Boolean,onChange:(Boolean)->Unit){ListItem({Text(label)},trailingContent={Switch(value,onChange)})}
@Composable private fun Empty(title:String,body:String){Column(Modifier.fillMaxWidth().padding(36.dp),horizontalAlignment=Alignment.CenterHorizontally){Text(title,fontSize=20.sp,fontWeight=FontWeight.Bold);Text(body,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
private fun isTodayOrFuture(i: Instant?): Boolean {
    if (i == null) return false
    val eventDate = i.atZone(ZoneId.systemDefault()).toLocalDate()
    return !eventDate.isBefore(LocalDate.now())
}

private fun relative(i:Instant?):String{if(i==null)return "Date to be confirmed";val h=java.time.Duration.between(Instant.now(),i).toHours();return when{h<0->"Past";h<24->"Today · ${h}h left";h<48->"Tomorrow";else->"${h/24} days"}}

@Composable private fun ClassPingTheme(mode:ThemeMode,content:@Composable()->Unit){val dark=when(mode){ThemeMode.DARK->true;ThemeMode.LIGHT->false;ThemeMode.SYSTEM->isSystemInDarkTheme()}; val colors=if(dark) darkColorScheme(primary=Color(0xFFD0BCFF),surface=Color(0xFF141218),surfaceVariant=Color(0xFF49454F)) else lightColorScheme(primary=Color(0xFF6750A4),surface=Color(0xFFFFFBFE),surfaceVariant=Color(0xFFF3EDF7));MaterialTheme(colorScheme=colors,typography=Typography()) { content() }}
