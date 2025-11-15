package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.myapplication.ui.ChatScreen
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.agents.ChatManager

class ChatActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ChatManager.initialize(this)

        setContent {
            val ctx = LocalContext.current
            val prefs = PrefsHelper(ctx)
            MyApplicationTheme(darkTheme = prefs.isDarkTheme()) {
              val scope = rememberCoroutineScope()

              var showDeveloper by rememberSaveable { mutableStateOf(prefs.getShowDeveloperDebug()) }
              var messages by remember { mutableStateOf(listOf<ChatManager.ChatMessage>()) }
              var input by rememberSaveable { mutableStateOf("") }

              // load initial messages
              LaunchedEffect(showDeveloper) {
                  withContext(Dispatchers.IO) {
                      val snap = if (showDeveloper) ChatManager.getHistorySnapshot()
                      else ChatManager.getHistorySnapshot().filter { it.role == "user" || it.role == "assistant" }
                      messages = snap
                  }
              }

              fun refresh() {
                  scope.launch(Dispatchers.IO) {
                      val snap = if (showDeveloper) ChatManager.getHistorySnapshot()
                      else ChatManager.getHistorySnapshot().filter { it.role == "user" || it.role == "assistant" }
                      withContext(Dispatchers.Main) { messages = snap }
                  }
              }

              fun clear() {
                  scope.launch(Dispatchers.IO) {
                      ChatManager.clearHistory(ctx)
                      val snap = if (showDeveloper) ChatManager.getHistorySnapshot()
                      else ChatManager.getHistorySnapshot().filter { it.role == "user" || it.role == "assistant" }
                      withContext(Dispatchers.Main) { messages = snap }
                  }
              }

              fun send(text: String) {
                  scope.launch(Dispatchers.IO) {
                      ChatManager.sendUserMessage(ctx, text)
                      val snap = if (showDeveloper) ChatManager.getHistorySnapshot()
                      else ChatManager.getHistorySnapshot().filter { it.role == "user" || it.role == "assistant" }
                      withContext(Dispatchers.Main) { messages = snap }
                  }
              }

              ChatScreen(
                  showDeveloper = showDeveloper,
                  onToggleShowDeveloper = { v ->
                      showDeveloper = v
                      prefs.setShowDeveloperDebug(v)
                      refresh()
                  },
                  messages = messages,
                  onRefresh = { refresh() },
                  onClear = { clear() },
                  onSend = { send(it) },
                  inputText = input,
                  onInputChange = { input = it }
              )
            }
         }
     }
 }
