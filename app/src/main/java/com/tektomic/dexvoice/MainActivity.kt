package com.tektomic.dexvoice

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/** Launcher entry: shows the setup checklist until the required items are done, then settings. */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val done = try { OnboardingActivity.requiredDone(this) } catch (_: Exception) { false }
        startActivity(Intent(this, if (done) SettingsActivity::class.java else OnboardingActivity::class.java))
        finish()
    }
}
