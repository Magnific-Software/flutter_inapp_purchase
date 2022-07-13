package com.dooboolab.flutterinapppurchase

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.amazon.device.drm.LicensingService
import com.amazon.device.iap.PurchasingListener
import com.amazon.device.iap.PurchasingService
import com.amazon.device.iap.model.*
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.*

/** AmazonInappPurchasePlugin  */
class AmazonInappPurchasePlugin(
    private var activity: Activity?,
    private val context: Context,
    private val channel: MethodChannel,
) : MethodCallHandler, Application.ActivityLifecycleCallbacks {
    private val tag = "InappPurchasePlugin"

    @VisibleForTesting
    internal object MethodNames {
        const val UPDATE_PACKAGE_INSTALLER = "Additional#updatePackageInstaller()"
        const val INITIALIZE = "AmazonIAPClient#initialize()"
        const val PLATFORM_VERSION = "AmazonIAPClient#getPlatformVersion()"
        const val CLIENT_INFORMATION = "AmazonIAPClient#getClientInformation()"
        const val CLIENT_INFORMATION_CALLBACK =
            "AmazonIAPClient#onClientInformation(AmazonUserData)"
        const val SDK_MODE = "AmazonIAPClient#getSDKMode()"
        const val LICENSE_VERIFICATION_RESPONSE_CALLBACK =
            "AmazonIAPClient#onLicenseVerificationResponse()"
        const val SKUS_CALLBACK = "AmazonIAPClient#onSKU(AmazonUserData)"
        const val PURCHASE_UPDATE_CALLBACK = "AmazonIAPClient#onPurchaseUpdate(AmazonUserData)"
        const val getAvailableItemsByType = "getAvailableItemsByType"
        const val getProducts = "getProducts"
        const val getSubscriptions = "getSubscriptions"
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        // registerPurchaseServiceListener(activity)
    }

    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {
        if (this.activity === activity) {
            (context as Application).unregisterActivityLifecycleCallbacks(this)
            // endBillingClientConnection()
        }
    }

    private var safeResult: MethodResultWrapper? = null
    private val safeResults = HashMap<String, MethodResultWrapper>()

    fun setActivity(activity: Activity?) {
        this.activity = activity
    }

    private var isListenerRegistered = false;

    private fun registerPurchaseServiceListener(context: Context) {
        if (isListenerRegistered) return;
        isListenerRegistered = true;
        try {
            PurchasingService.registerListener(context, purchasesUpdatedListener)
            Log.d(tag, "Registered purchasing service listener")
        } catch (e: Exception) {
            Log.e(
                tag,
                "Plugin failed to add purchase listener with error: ${e.message}"
            )
        }
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        safeResult = MethodResultWrapper(result, channel)

        safeResults[call.method] = safeResult!!

        registerPurchaseServiceListener(context)

        when (call.method) {
            "getStore" -> {
                result.success(FlutterInappPurchasePlugin.getStore())
                return
            }
            MethodNames.UPDATE_PACKAGE_INSTALLER -> {
                val pm = context.packageManager
                val packageName = context.packageName

                if (pm != null && packageName != null) {
                    try {
                        pm.setInstallerPackageName(packageName, call.arguments as String)
                        result.success(true)
                    } catch (e: Exception) {
                        result.error("PM_FAILED", "PM failed: ${e.message}", null)
                    }
                } else {
                    result.error("PM_FAILED", "PM failed", null)
                }
                return
            }
            MethodNames.INITIALIZE -> {
                try {
                    LicensingService.verifyLicense(
                        context
                    ) {
                        Log.d(tag, "License verification response ${it.requestStatus}")
                        channel.invokeMethod(
                            MethodNames.LICENSE_VERIFICATION_RESPONSE_CALLBACK,
                            it.requestStatus.name
                        )
                    }
                    result.success(true)
                } catch (e: Exception) {
                    result.error(
                        "LICENSING_VERIFICATION_FAILED",
                        "Failed verification ${e.message}",
                        e.message
                    )
                }
            }
            MethodNames.PLATFORM_VERSION -> {
                result.success("Android ${android.os.Build.VERSION.RELEASE}")
            }
            MethodNames.CLIENT_INFORMATION -> {
                val data = PurchasingService.getUserData()
                Log.d(tag, "Requesting user data from purchasing service: ${data.toJSON()}")
                Log.d(tag, "Appstore SDK Mode: " + LicensingService.getAppstoreSDKMode())
                result.success(true)
            }
            MethodNames.SDK_MODE -> {
                result.success(LicensingService.getAppstoreSDKMode())
            }
            "initConnection" -> {
                PurchasingService.getUserData()
                result.success("Billing client ready")
            }
            "endConnection" -> {
                result.success("Billing client has ended.")
            }
            "isReady" -> {
                result.success(true)
            }
            "showInAppMessages" -> {
                result.success("in app messages not supported for amazon")
            }
            "consumeAllItems" -> {
                // consumable is a separate type in amazon
                result.success("no-ops in amazon")
            }
            "acknowledgePurchase" -> {
                result.success("no-ops in amazon")
            }
            MethodNames.getProducts,
            MethodNames.getSubscriptions -> {
                Log.d(tag, call.method)
                val skus = call.argument<ArrayList<String>>("skus")!!
                val productSkus: MutableSet<String> = HashSet()
                for (i in skus.indices) {
                    Log.d(tag, "Adding " + skus[i])
                    productSkus.add(skus[i])
                }
                PurchasingService.getProductData(productSkus)
            }
            MethodNames.getAvailableItemsByType -> {
                val type = call.argument<String>("type")
                Log.d(tag, "gaibt=$type")
                // NOTE: getPurchaseUpdates doesnt return Consumables which are FULFILLED
                when (type) {
                    "inapp" -> {
                        PurchasingService.getPurchaseUpdates(true)
                    }
                    "subs" -> {
                        // Subscriptions are retrieved during inapp, so we just return empty list
                        result.success("[]")
                    }
                    else -> {
                        result.notImplemented()
                    }
                }
            }
            "getPurchaseHistoryByType" -> {
                // No equivalent
                result.success("[]")
            }
            "buyItemByType" -> {
                val type = call.argument<String>("type")
                //val obfuscatedAccountId = call.argument<String>("obfuscatedAccountId")
                //val obfuscatedProfileId = call.argument<String>("obfuscatedProfileId")
                val sku = call.argument<String>("sku")
                val oldSku = call.argument<String>("oldSku")
                //val prorationMode = call.argument<Int>("prorationMode")!!
                Log.d(tag, "type=$type||sku=$sku||oldsku=$oldSku")
                val requestId = PurchasingService.purchase(sku)
                Log.d(tag, "resid=$requestId")
            }
            "consumeProduct" -> {
                // consumable is a separate type in amazon
                result.success("no-ops in amazon")
            }
            else -> {
                result.notImplemented()
            }
        }
    }

    private val purchasesUpdatedListener: PurchasingListener = object : PurchasingListener {
        override fun onUserDataResponse(response: UserDataResponse) {
            Log.d(tag, "Received user data response: $response")
            try {
                val status = response.requestStatus
                var currentUserId: String? = null
                var currentMarketplace: String? = null
                val statusValue: String

                when (status) {
                    UserDataResponse.RequestStatus.SUCCESSFUL -> {
                        currentUserId = response.userData.userId
                        currentMarketplace = response.userData.marketplace
                        statusValue = "SUCCESSFUL"
                    }
                    UserDataResponse.RequestStatus.FAILED, null -> statusValue = "FAILED"
                    // Fail gracefully.
                    UserDataResponse.RequestStatus.NOT_SUPPORTED ->
                        statusValue = "NOT_SUPPORTED"
                }

                val item: HashMap<String, Any?> = HashMap()

                item["userId"] = currentUserId
                item["marketplace"] = currentMarketplace
                item["status"] = statusValue

                Log.d(tag, "Putting data: $item")
                channel.invokeMethod(MethodNames.CLIENT_INFORMATION_CALLBACK, item)
            } catch (e: Exception) {
                Log.e(tag, "ON_USER_DATA_RESPONSE_JSON_PARSE_ERROR: ${e.message}")
                safeResult?.error("ON_USER_DATA_RESPONSE_JSON_PARSE_ERROR", e.message, null)
            }
        }

        // getItemsByType
        override fun onProductDataResponse(response: ProductDataResponse) {
            Log.d(tag, "opdr=$response")
            val status = response.requestStatus
            Log.d(tag, "onProductDataResponse: RequestStatus ($status)")
            when (status) {
                ProductDataResponse.RequestStatus.SUCCESSFUL -> {
                    Log.d(
                        tag,
                        "onProductDataResponse: successful.  The item data map in this response includes the valid SKUs"
                    )
                    val productData = response.productData
                    //Log.d(TAG, "productData="+productData.toString());
                    val unavailableSkus = response.unavailableSkus
                    Log.d(
                        tag,
                        "onProductDataResponse: " + unavailableSkus.size + " unavailable skus"
                    )
                    Log.d(tag, "unavailableSkus=$unavailableSkus")
                    val items = JSONArray()
                    try {
                        for ((_, product) in productData) {
                            //val format = NumberFormat.getCurrencyInstance()
                            val item = JSONObject()
                            item.put("productId", product.sku)
                            item.put("price", product.price)
                            item.put("currency", null)
                            when (product.productType) {
                                ProductType.ENTITLED, ProductType.CONSUMABLE -> item.put(
                                    "type",
                                    "inapp"
                                )
                                ProductType.SUBSCRIPTION -> item.put("type", "subs")
                                null -> item.put("type", "unknown")
                            }
                            item.put("localizedPrice", product.price)
                            item.put("title", product.title)
                            item.put("description", product.description)
                            item.put("introductoryPrice", "")
                            item.put("subscriptionPeriodAndroid", "")
                            item.put("freeTrialPeriodAndroid", "")
                            item.put("introductoryPriceCyclesAndroid", 0)
                            item.put("introductoryPricePeriodAndroid", "")
                            Log.d(tag, "opdr Putting $item")
                            items.put(item)
                        }
                        //System.err.println("Sending "+items.toString());
                        channel.invokeMethod(MethodNames.SKUS_CALLBACK, items.toString())
                        safeResult?.success(items.toString())
                    } catch (e: JSONException) {
                        safeResult?.error(tag, "E_BILLING_RESPONSE_JSON_PARSE_ERROR", e.message)
                    }
                }
                null,
                ProductDataResponse.RequestStatus.FAILED -> {
                    safeResult?.error(tag, "FAILED", null)
                    Log.d(tag, "onProductDataResponse: failed, should retry request")
                    safeResult?.error(tag, "NOT_SUPPORTED", null)
                }
                ProductDataResponse.RequestStatus.NOT_SUPPORTED -> {
                    Log.d(tag, "onProductDataResponse: failed, should retry request")
                    safeResult?.error(tag, "NOT_SUPPORTED", null)
                }
            }
        }

        // buyItemByType
        override fun onPurchaseResponse(response: PurchaseResponse) {
            Log.d(tag, "opr=$response")
            when (val status = response.requestStatus) {
                PurchaseResponse.RequestStatus.SUCCESSFUL -> {
                    val receipt = response.receipt
                    PurchasingService.notifyFulfillment(
                        receipt.receiptId,
                        FulfillmentResult.FULFILLED
                    )
                    try {
                        val item = onPurchaseReceipt(
                            receipt,
                        )
                        Log.d(tag, "opr Putting $item")
                        channel.invokeMethod("purchase-updated", item.toString())
                        safeResult?.success(item.toString())
                    } catch (e: JSONException) {
                        safeResult?.error(tag, "E_BILLING_RESPONSE_JSON_PARSE_ERROR", e.message)
                    }
                }
                null,
                PurchaseResponse.RequestStatus.FAILED -> safeResult?.error(
                    tag,
                    "buyItemByType",
                    "billingResponse is not ok: $status"
                )
                else -> {
                    safeResult?.error(
                        tag,
                        "buyItemByType",
                        "billingResponse is not ok: $status"
                    )
                }
            }
        }

        // getAvailableItemsByType
        override fun onPurchaseUpdatesResponse(response: PurchaseUpdatesResponse) {
            Log.d(tag, "opudr=$response")
            when (response.requestStatus) {
                PurchaseUpdatesResponse.RequestStatus.SUCCESSFUL -> {
                    val items = JSONArray()
                    try {
                        val receipts = response.receipts
                        for (receipt in receipts) {
                            val item = onPurchaseReceipt(
                                receipt,
                            )
                            Log.d(tag, "opudr Putting $item")
                            items.put(item)
                        }

                        channel.invokeMethod(MethodNames.PURCHASE_UPDATE_CALLBACK, items.toString())
                        success(MethodNames.getAvailableItemsByType, items.toString())
                        success(MethodNames.getProducts, items.toString())
                        success(MethodNames.getSubscriptions, items.toString())
                    } catch (e: JSONException) {
                        error(
                            MethodNames.getAvailableItemsByType,
                            tag,
                            "E_BILLING_RESPONSE_JSON_PARSE_ERROR",
                            e.message
                        )
                        error(
                            MethodNames.getProducts,
                            tag,
                            "E_BILLING_RESPONSE_JSON_PARSE_ERROR",
                            e.message
                        )
                        error(
                            MethodNames.getSubscriptions,
                            tag,
                            "E_BILLING_RESPONSE_JSON_PARSE_ERROR",
                            e.message
                        )
                    }
                }
                null,
                PurchaseUpdatesResponse.RequestStatus.FAILED -> {
                    error(
                        MethodNames.getAvailableItemsByType, tag,
                        "FAILED",
                        null
                    )
                    error(
                        MethodNames.getProducts, tag,
                        "FAILED",
                        null
                    )
                    error(
                        MethodNames.getSubscriptions, tag,
                        "FAILED",
                        null
                    )
                }
                PurchaseUpdatesResponse.RequestStatus.NOT_SUPPORTED -> {
                    Log.d(tag, "onPurchaseUpdatesResponse: failed, should retry request")
                    error(MethodNames.getAvailableItemsByType, tag, "NOT_SUPPORTED", null)
                    error(MethodNames.getProducts, tag, "NOT_SUPPORTED", null)
                    error(MethodNames.getSubscriptions, tag, "NOT_SUPPORTED", null)
                }
            }
        }
    }

    @Throws(JSONException::class)
    fun onPurchaseReceipt(
        receipt: Receipt,
    ): JSONObject {
        val item = JSONObject()
        item.put("productId", receipt.sku)
        item.put("transactionId", receipt.receiptId)
        item.put("transactionReceipt", receipt.receiptId)
        item.put("transactionDate", dateToString(receipt.purchaseDate))
        item.put("dataAndroid", null)
        item.put("signatureAndroid", null)
        item.put("purchaseToken", null)

        val extra = JSONObject()

        val extraAmazon = JSONObject()
        extraAmazon.put("productType", receipt.productType.name)
        extraAmazon.put("cancelDate", dateToString(receipt.cancelDate))
        extraAmazon.put("deferredDate", dateToString(receipt.deferredDate))
        extraAmazon.put("deferredSku", receipt.deferredSku)
        extraAmazon.put("termSku", receipt.termSku)

        extra.put("amazon", extraAmazon)
        item.put("extra", extra)
        return item
    }

    private fun dateToString(date: Date?): String? {
        return date?.time?.toDouble()?.toString()
    }

    private fun success(name: String, result: Any?) {
        if (safeResults[name] == null) {
            Log.w(tag, "safe result already used for $name")
            return
        }
        safeResults[name]?.success(result)
        Log.w(tag, "sent result for $name")
        safeResults.remove(name)
    }

    private fun error(name: String, errorCode: String, errorMessage: String?, errorDetails: Any?) {
        if (safeResults[name] == null) {
            Log.w(tag, "safe result already used for $name")
            return
        }
        safeResults[name]?.error(errorCode, errorMessage, errorDetails)
        safeResults.remove(name)
    }
}