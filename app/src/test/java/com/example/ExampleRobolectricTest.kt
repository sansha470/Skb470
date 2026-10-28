package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Sant Browser", appName)
  }

  @Test
  fun `adblocker identifies ad hosts`() {
    val isDoubleclickAd = com.example.core.adblocker.AdBlocker.isAd("https://adservice.google.com/ads?id=123")
    assertEquals(true, isDoubleclickAd)
    val isNormalUrl = com.example.core.adblocker.AdBlocker.isAd("https://en.wikipedia.org/wiki/Android")
    assertEquals(false, isNormalUrl)
  }
}
