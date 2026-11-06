package app.embeddingspace.explorer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import app.embeddingspace.explorer.ui.AppRoot

class MainActivity : ComponentActivity() {
    private val vm: ExplorerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { AppRoot(vm) }
    }
}
