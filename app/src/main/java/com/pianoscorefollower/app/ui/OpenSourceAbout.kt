package com.pianoscorefollower.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.pianoscorefollower.app.BuildConfig

@Composable
fun OpenSourceAbout() {
    val uri=LocalUriHandler.current
    val context=LocalContext.current
    var license by remember { mutableStateOf<Pair<String,String>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    fun open(url:String){runCatching{uri.openUri(url)}.onFailure{error="无法打开链接，请检查浏览器"}}
    fun show(title:String,asset:String){
        runCatching{context.assets.open("licenses/$asset").bufferedReader().use{it.readText()}}
            .onSuccess{license=title to it}.onFailure{error="无法读取协议文件"}
    }
    Surface(shape=MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("智能琴谱 ${BuildConfig.VERSION_NAME}",style=MaterialTheme.typography.titleMedium)
            Text("Jimmy-xuzimo · 源码采用 BSD 3-Clause；第三方代码、模型、字体与音源各自保留原协议。",style=MaterialTheme.typography.bodySmall)
            TextButton(onClick={open("https://github.com/Jimmy-xuzimo/PianoScoreFollower")}){Text("GitHub 项目与源码")}
            TextButton(onClick={open("https://jimmy-xuzimo.github.io/PianoScoreFollower/")}){Text("项目介绍网页")}
            TextButton(onClick={show("智能琴谱 · BSD 3-Clause","project-BSD-3-Clause.txt")}){Text("查看应用开源协议")}
            HorizontalDivider()
            Text("使用的开源项目",style=MaterialTheme.typography.titleSmall)
            Text("Google Jetpack Compose Material 3 / AndroidX、Kotlin、协程、LiteRT、Magenta Onsets and Frames、Parangonar（Apache 2.0）；alphaTab（MPL 2.0），包含 TinySoundFont、SFZero、Haxe、SharpZipLib、NVorbis 与 libvorbis；Bravura（SIL OFL）；FreePats Upright Piano KW 音源（CC0）。",style=MaterialTheme.typography.bodySmall)
            listOf(
                Triple("第三方项目、来源与署名","THIRD_PARTY_NOTICES.txt",""),
                Triple("Apache 2.0 · AndroidX / Kotlin / LiteRT / Magenta / Parangonar","magenta-LICENSE.txt",""),
                Triple("Magenta 模型来源","magenta-NOTICE.txt",""),
                Triple("Parangonar 算法来源","parangonar-NOTICE.txt",""),
                Triple("alphaTab · MPL 2.0","alphaTab-MPL-2.0.txt",""),
                Triple("alphaTab 内含组件署名","alphaTab-NOTICE.txt",""),
                Triple("alphaTab 内含组件 · MIT","integrated-MIT.txt",""),
                Triple("libvorbis · BSD 3-Clause","libvorbis-BSD.txt",""),
                Triple("Bravura · SIL OFL","Bravura-OFL.txt",""),
                Triple("钢琴音源 · CC0","piano-CC0.txt","")
            ).forEach{(title,file,_)->TextButton(onClick={show(title,file)}){Text(title)}}
            error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        }
    }
    license?.let{(title,text)->AlertDialog(onDismissRequest={license=null},title={Text(title)},text={Text(text,Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),style=MaterialTheme.typography.bodySmall)},confirmButton={TextButton(onClick={license=null}){Text("关闭")}})}
}
