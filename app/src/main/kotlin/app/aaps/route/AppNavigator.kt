package app.aaps.route

import android.content.Context
import android.content.Intent
import app.aaps.MainActivity
import app.aaps.plugins.auth.route.Navigator
import javax.inject.Inject

class AppNavigator @Inject constructor(
    private val context: Context
) : Navigator {
    override fun navigateToMain() {
        val intent = Intent(context, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent)
    }
}