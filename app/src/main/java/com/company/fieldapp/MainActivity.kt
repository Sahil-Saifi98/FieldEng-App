package com.company.fieldapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.company.fieldapp.data.remote.RetrofitClient
import com.company.fieldapp.data.session.SessionManager
import com.company.fieldapp.navigation.AppNavGraph
import com.company.fieldapp.ui.theme.FieldEngineerAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set auth token so all API calls are authenticated
        val sessionManager = SessionManager(this)
        RetrofitClient.setAuthToken(sessionManager.getToken())

        setContent {
            FieldEngineerAppTheme {
                AppNavGraph()
            }
        }
    }
}