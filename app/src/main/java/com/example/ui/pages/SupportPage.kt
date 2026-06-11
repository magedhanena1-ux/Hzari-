package com.example.ui.pages

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class SupportMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: String = "الآن"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SupportPage(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 1. Chat State
    var messages by remember {
        mutableStateOf(
            listOf(
                SupportMessage(
                    "مرحباً بك! فريق الدعم الفني لحذاري جاهز لمساعدتك في أي وقت. كيف يمكننا خدمتك اليوم؟",
                    isUser = false
                )
            )
        )
    }
    var chatInput by remember { mutableStateOf("") }
    var isTyping by remember { mutableStateOf(false) }
    val chatListState = rememberLazyListState()

    // Scroll to bottom when messages list size changes
    LaunchedEffect(messages.size, isTyping) {
        if (messages.isNotEmpty()) {
            chatListState.animateScrollToItem(messages.size - 1)
        }
    }

    fun handleIncomingMessage(userText: String) {
        if (userText.trim().isEmpty()) return
        
        // Add user message
        messages = messages + SupportMessage(userText.trim(), isUser = true)
        chatInput = ""
        isTyping = true

        scope.launch {
            delay(1500) // Simulated typing delay
            val replyText = when {
                userText.contains("تحديث") || userText.contains("اصدار") || userText.contains("الادوات") -> {
                    "لتنزيل الأدوات والموارد الجديدة وتحديث التطبيق، يمكنك التوجه إلى 'قسم التحديثات' داخل الإعدادات أو الصفحة الرئيسية، وتفعيل زر 'التحديث التلقائي' أو النقر على 'بدء التحديث المباشر' لتنزيلها فورياً بلمسة واحدة."
                }
                userText.contains("مسح") || userText.contains("باركود") || userText.contains("كاميرا") -> {
                    "إذا واجهتك مشكلة في مسح الباركود، يرجى التأكد من تزويد التطبيق بصلاحية الكاميرا في الإعدادات، واستخدام إضاءة جيدة. كما يدعم التطبيق إدخال الرموز يدوياً لحماية إنتاجيتك في حال عطل الكاميرا."
                }
                userText.contains("مزامنة") || userText.contains("غير متصل") || userText.contains("دون اتصال") -> {
                    "أهلاً بك، يدعم تطبيق حذاري واجهة مزامنة ذكية تحفظ أصنافك محلياً بالكامل عند انقطاع الإنترنت. فور استرجاع الاتصال، تفضل بزيارة صفحة 'المزامنة اليدوية' واضغط على زر 'بدء المزامنة' لرفعها دفعة واحدة للخادم بأمان."
                }
                userText.contains("اكسل") || userText.contains("ملف") || userText.contains("رفع") -> {
                    "لرفع المنتجات بصيغة Excel، يرجى النقر على صفحة 'رفع Excel' في القائمة الجانبية وتنزيل النموذج القياسي المعتمد، والتأكد من ترتيب الحقول (اسم الصنف، الباركود، التاريخ، الموقع) لتفادي أخطاء الرفع."
                }
                else -> {
                    "أشكرك على رسالتك! تم توجيه استفسارك العاجل لمهندسي الدعم الفني وسيتصل بك أحد الفنيين على رقمك المسجل فوراً. يمكنك أيضاً ملء استمارة التذكرة الرسمية بالأسفل لتلقي تقرير متكامل."
                }
            }
            messages = messages + SupportMessage(replyText, isUser = false)
            isTyping = false
        }
    }

    // 2. Ticket State
    var ticketSubject by remember { mutableStateOf("") }
    var ticketCategory by remember { mutableStateOf("الأعطال والمسح") }
    var ticketPriority by remember { mutableStateOf("متوسطة") }
    var ticketDetails by remember { mutableStateOf("") }
    var fileUriMocked by remember { mutableStateOf<String?>(null) }

    var showTicketSuccessDialog by remember { mutableStateOf(false) }
    var generatedTicketId by remember { mutableStateOf("") }

    val categories = listOf("الأعطال والمسح", "المزامنة والاتصال", "تحديثات النظام", "إدارة المخزون والرفع", "أخرى")
    val priorities = listOf("عادية", "متوسطة", "عاجلة جداً")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Upper Support welcome banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SupportAgent,
                            contentDescription = "",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "مركز الدعم الفني لحذاري",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "متواجدون على مدار الساعة لخدمتكم وتسيير أعمالكم",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }

        // Section 1: Chat Assistant Room
        Text(
            text = "مساعد الدعم الفني الذكي الحاضر",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(350.dp)
                .testTag("support_chat_card"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Chat Room Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF22C55E))
                        )
                        Text(
                            text = "دردشة الدعم الفوري ومستشار حذاري",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }

                // Chat Messages Window
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
                        .padding(12.dp)
                ) {
                    LazyColumn(
                        state = chatListState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(messages) { msg ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
                            ) {
                                Box(
                                    modifier = Modifier
                                        .widthIn(max = 260.dp)
                                        .clip(
                                            RoundedCornerShape(
                                                topStart = 12.dp,
                                                topEnd = 12.dp,
                                                bottomStart = if (msg.isUser) 12.dp else 0.dp,
                                                bottomEnd = if (msg.isUser) 0.dp else 12.dp
                                            )
                                        )
                                        .background(
                                            if (msg.isUser) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .padding(12.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = msg.text,
                                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                                            color = if (msg.isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = msg.timestamp,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.align(Alignment.End),
                                            color = if (msg.isUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                        )
                                    }
                                }
                            }
                        }

                        if (isTyping) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Start
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .padding(horizontal = 14.dp, vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = "جاري الكتابة ومسح طلبك...",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Chat Quick Suggestions Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val quickQueries = listOf(
                        "كيف افعل التحديث التلقائي للأدوات؟",
                        "مشكلة في مسح الباركود بالكاميرا",
                        "كيفية العمل والرفع دون إنترنت"
                    )
                    quickQueries.forEach { query ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f))
                                .clickable { handleIncomingMessage(query) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = query,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // Chat input bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = chatInput,
                        onValueChange = { chatInput = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("أدخل سؤالك هنا...") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send)
                    )

                    IconButton(
                        onClick = {
                            if (chatInput.isNotBlank()) {
                                handleIncomingMessage(chatInput)
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)
                    ) {
                        Icon(imageVector = Icons.Default.Send, contentDescription = "إرسال")
                    }
                }
            }
        }

        // Section 2: Support Ticket Submission Form
        Text(
            text = "حجز تذكرة دعم ومتابعة الأعطال السحابية",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Ticket info instructions
                Text(
                    text = "إنشاء تذكرة يرسل تقريراً شاملاً لمهندسين الصيانة فوراً، وسنقوم بالرد عليكم عبر البريد والتنبيهات المباشرة.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                // Subject Outlined Field
                OutlinedTextField(
                    value = ticketSubject,
                    onValueChange = { ticketSubject = it },
                    label = { Text("موضوع التذكرة / المشكلة باختصار *") },
                    modifier = Modifier.fillMaxWidth().testTag("subject_input"),
                    placeholder = { Text("مثال: عطل في تحديث الأصناف، تعذر تصدير Excel") },
                    shape = RoundedCornerShape(8.dp)
                )

                // Category Selection
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "تصنيف المشكلة والبيئة الخاصة بها:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        categories.forEach { cat ->
                            val selected = ticketCategory == cat
                            FilterChip(
                                selected = selected,
                                onClick = { ticketCategory = cat },
                                label = { Text(cat) },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                }

                // Priority Selector
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "درجة الأولوية والأهمية بالنسبة لعملك:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        priorities.forEach { priority ->
                            val selected = ticketPriority == priority
                            val btnColor = when (priority) {
                                "عاجلة جداً" -> Color(0xFFEF4444)
                                "متوسطة" -> Color(0xFFF59E0B)
                                else -> Color(0xFF10B981)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (selected) btnColor.copy(alpha = 0.15f)
                                        else Color.Transparent
                                    )
                                    .clickable { ticketPriority = priority }
                                    .padding(vertical = 10.dp, horizontal = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(btnColor)
                                    )
                                    Text(
                                        text = priority,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
                                        color = if (selected) btnColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                }

                // Details Textbox
                OutlinedTextField(
                    value = ticketDetails,
                    onValueChange = { ticketDetails = it },
                    label = { Text("شرح المشكلة بالتفصيل والخطوات المطلوبة لإصلاحها *") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .testTag("details_input"),
                    placeholder = { Text("الرجاء كتابة خطوات حدوث العطل بالتفصيل ليتمكن المهندسون من محاكاته وحله سريعا...") },
                    shape = RoundedCornerShape(8.dp)
                )

                // Optional Mock Screen Upload Attachment
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .clickable {
                            fileUriMocked = "Screenshoot_Error_Log_2026.png"
                            Toast.makeText(context, "تم إرفاق لقطة شاشة تقرير الخطأ التلقائي بنجاح!", Toast.LENGTH_SHORT).show()
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (fileUriMocked == null) Icons.Default.CloudUpload else Icons.Default.Attachment,
                        contentDescription = "",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (fileUriMocked == null) "إرفاق لقطة شاشة للخطأ (اختياري)" else "تم إرفاق: $fileUriMocked",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Submit Form Button
                Button(
                    onClick = {
                        if (ticketSubject.trim().isEmpty() || ticketDetails.trim().isEmpty()) {
                            Toast.makeText(context, "يرجى تعبئة الحقول الإلزامية ممثلة بالنجمة (*)", Toast.LENGTH_LONG).show()
                            return@Button
                        }
                        
                        generatedTicketId = "HATHARI-${(10000..99999).random()}"
                        showTicketSuccessDialog = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("submit_ticket_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.ConfirmationNumber, contentDescription = "")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("رفع تذكرة الدعم ومعالحة العطل الآن", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // Section 3: Quick Direct Contact channels
        Text(
            text = "قنوات الاتصال المباشرة والسريعة",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val contactOptions = listOf(
                Triple("الوتساب", Icons.Default.Chat, "https://wa.me/966500000000"), // Mock support WhatsApp
                Triple("اتصل بنا", Icons.Default.Phone, "tel:+966500000000"),
                Triple("الإيميل", Icons.Default.MailOutline, "mailto:support@hathari-scanner.com")
            )

            contactOptions.forEach { (label, icon, address) ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(address))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "الخدمة أو التطبيق غير متوفر: تم حش حركات الاتصال بنجاح لـ $label", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }

    // Success dialog for registered Tickets
    if (showTicketSuccessDialog) {
        AlertDialog(
            onDismissRequest = {
                showTicketSuccessDialog = false
                ticketSubject = ""
                ticketDetails = ""
                fileUriMocked = null
            },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "", tint = Color(0xFF10B981))
                    Text("تم رفع التذكرة بنجاح", color = Color(0xFF14532D), fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "نقدر تواصلك معنا لمنع العطل وتحسين أداء التطبيق.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "رقم تذكرة الدعم السحابية:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            Text(text = generatedTicketId, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Text(
                        text = "جاري الآن عرضها في لوحة تحكم الفنيين، وقد نقوم بالاتصال بك فوراً لإتمام الحل.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showTicketSuccessDialog = false
                        ticketSubject = ""
                        ticketDetails = ""
                        fileUriMocked = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Text("موافق (إغلاق نموذج التذكرة)", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}


