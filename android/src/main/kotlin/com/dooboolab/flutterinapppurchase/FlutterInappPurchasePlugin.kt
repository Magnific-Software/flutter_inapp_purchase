package com.dooboolab.flutterinapppurchase

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager.NameNotFoundException
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.FlutterPlugin.FlutterPluginBinding
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.PluginRegistry.Registrar

/** FlutterInappPurchasePlugin  */
class FlutterInappPurchasePlugin : FlutterPlugin, ActivityAware {
    private var androidInappPurchasePlugin: AndroidInappPurchasePlugin? = null
    private var amazonInappPurchasePlugin: AmazonInappPurchasePlugin? = null
    private var channel: MethodChannel? = null

    override fun onAttachedToEngine(binding: FlutterPluginBinding) {
        setupMethodChannel( /*activity=*/
            null, binding.binaryMessenger, binding.applicationContext)
    }

    private fun setupMethodChannel(
        activity: Activity?,
        messenger: BinaryMessenger,
        context: Context,
    ) {
        isAndroid = isPackageInstalled(context, "com.android.vending")
        isAmazon = isPackageInstalled(context, "com.amazon.venezia")

        // In the case of an amazon device which has been side loaded with the Google Play store,
        // we should use the store the app was installed from.
        if (isAmazon && isAndroid) {
            if (isAppInstalledFrom(context, "amazon")) {
                isAndroid = false
            } else {
                isAmazon = false
            }
        }
        channel = MethodChannel(messenger, "flutter_inapp")

        if (isAndroid) {
            androidInappPurchasePlugin = AndroidInappPurchasePlugin()
            androidInappPurchasePlugin!!.setContext(context)
            androidInappPurchasePlugin!!.setChannel(channel)
            channel!!.setMethodCallHandler(androidInappPurchasePlugin)
        } else if (isAmazon) {
            amazonInappPurchasePlugin = AmazonInappPurchasePlugin(
                activity,
                context,
                channel!!,
            )
            channel!!.setMethodCallHandler(amazonInappPurchasePlugin)
            (context.applicationContext as Application).registerActivityLifecycleCallbacks(
                amazonInappPurchasePlugin
            )
        }
    }

    override fun onDetachedFromEngine(binding: FlutterPluginBinding) {
        channel!!.setMethodCallHandler(null)
        channel = null
        if (isAndroid) {
            androidInappPurchasePlugin!!.setChannel(null)
        } else if (isAmazon) {
            // amazonInappPurchasePlugin!!.setChannel(null)
        }
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        if (isAndroid) {
            androidInappPurchasePlugin!!.setActivity(binding.activity)
        } else if (isAmazon) {
            amazonInappPurchasePlugin!!.setActivity(binding.activity)
        }
    }

    override fun onDetachedFromActivity() {
        if (isAndroid) {
            androidInappPurchasePlugin!!.setActivity(null)
            androidInappPurchasePlugin!!.onDetachedFromActivity()
        } else if (isAmazon) {
            amazonInappPurchasePlugin!!.setActivity(null)
        }
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        onAttachedToActivity(binding)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        onDetachedFromActivity()
    }

    private fun setAndroidInappPurchasePlugin(androidInappPurchasePlugin: AndroidInappPurchasePlugin) {
        this.androidInappPurchasePlugin = androidInappPurchasePlugin
    }

    private fun setAmazonInappPurchasePlugin(amazonInappPurchasePlugin: AmazonInappPurchasePlugin) {
        this.amazonInappPurchasePlugin = amazonInappPurchasePlugin
    }

    companion object {
        private var isAndroid = false
        private var isAmazon = false

        fun getStore(): String {
            return if (!isAndroid && !isAmazon) "none" else if (isAndroid) "play_store" else "amazon"
        }

        private fun isPackageInstalled(ctx: Context, packageName: String): Boolean {
            return try {
                ctx.packageManager.getPackageInfo(packageName, 0)
                true
            } catch (e: NameNotFoundException) {
                false
            }
        }

        fun isAppInstalledFrom(ctx: Context, installer: String?): Boolean {
            val installerPackageName = ctx.packageManager.getInstallerPackageName(ctx.packageName)
            return installer != null && installerPackageName != null && installerPackageName.contains(
                installer
            )
        }
    }
}