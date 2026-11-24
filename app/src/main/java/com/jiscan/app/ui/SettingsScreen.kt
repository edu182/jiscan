package com.jiscan.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiscan.app.data.DocumentStore
import com.jiscan.app.ocr.OcrEngine
import com.jiscan.app.ui.theme.InkGreen

@Composable
fun SettingsScreen(ocr: OcrEngine, store: DocumentStore) {
    val scroll = rememberScrollState()
    var settings by remember { mutableStateOf(store.getSettings()) }
    var saved by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(scroll)) {
        Text("设置", fontSize = 22.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(16.dp))

        // Identity card — matches the sketch: signature / department / naming rule.
        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp)) {
                SettingField("签　名", settings.signature, "如 Almighty119") {
                    settings = settings.copy(signature = it); saved = false
                }
                SettingField("单位部门", settings.department, "如 XX部门") {
                    settings = settings.copy(department = it); saved = false
                }
                Spacer(Modifier.height(8.dp))
                Text("命名规则", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = InkGreen)
                Text("YYMMDD+[部门]+[签名]+事由", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text("示例：260424_XX部门_Almighty119_合同", fontSize = 12.sp, color = Color.Gray)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { store.saveSettings(settings); saved = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (saved) "已保存" else "保存签名与部门")
                }
            }
        }

        // Privacy card.
        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lock, null, tint = InkGreen)
                    Spacer(Modifier.width(8.dp))
                    Text("隐私与安全", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Spacer(Modifier.height(10.dp))
                PrivacyLine("无网络权限 · 文件不会离开手机")
                PrivacyLine("仅申请相机权限")
                PrivacyLine("OCR 在本地离线完成")
                PrivacyLine("无账号 · 无广告 · 无水印 · 无追踪")
                PrivacyLine("开源代码可自行审计")
            }
        }

        // OCR status.
        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.TextFields, null, tint = InkGreen)
                    Spacer(Modifier.width(8.dp))
                    Text("OCR 语言", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Spacer(Modifier.height(10.dp))
                val available = ocr.availableLanguages()
                val ready = available.contains("chi_sim")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, null,
                        tint = if (ready) InkGreen else Color.Gray,
                        modifier = Modifier.width(18.dp).height(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (ready) "简体中文（chi_sim）已就绪" else "未检测到中文语言包", fontSize = 14.sp)
                }
            }
        }

        // About.
        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Info, null, tint = InkGreen)
                    Spacer(Modifier.width(8.dp))
                    Text("关于 简扫 JiScan", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text("本软件纯净、免费、开源。", fontSize = 14.sp)
                Text("扫描 · 自动校正 · 离线OCR · PDF导出", fontSize = 13.sp, color = Color.Gray)
                HorizontalDivider(Modifier.padding(vertical = 10.dp))
                Text("版本 1.0 · 许可 Apache-2.0", fontSize = 12.sp, color = Color.Gray)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SettingField(label: String, value: String, placeholder: String, onChange: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(72.dp))
        OutlinedTextField(
            value = value, onValueChange = onChange,
            placeholder = { Text(placeholder, fontSize = 12.sp) },
            singleLine = true, modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PrivacyLine(text: String) {
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("·", color = InkGreen, fontSize = 16.sp)
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 14.sp)
    }
}
