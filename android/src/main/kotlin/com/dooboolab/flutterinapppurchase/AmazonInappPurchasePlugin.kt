package com.dooboolab.flutterinapppurchase

import android.app.Activity
import android.content.Context
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

/** AmazonInappPurchasePlugin  */
class AmazonInappPurchasePlugin : MethodCallHandler {
    private val TAG = "InappPurchasePlugin"

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
    }

    private var safeResult: MethodResultWrapper? = null
    private var channel: MethodChannel? = null
    private var context: Context? = null
    private var activity: Activity? = null
    fun setContext(context: Context?) {
        this.context = context
    }

    fun setActivity(activity: Activity?) {
        this.activity = activity
    }

    fun setChannel(channel: MethodChannel?) {
        this.channel = channel
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        if(call.method == "getStore"){
            result.success(FlutterInappPurchasePlugin.getStore())
            return
        } else if (call.method == MethodNames.UPDATE_PACKAGE_INSTALLER) {
            val pm = context?.packageManager;
            val packageName = context?.packageName;

            if (pm != null && packageName != null) {
                try {
                    pm.setInstallerPackageName(packageName, call.arguments as String);
                    result.success(true)
                } catch (e: Exception) {
                    result.error("PM_FAILED", "PM failed: ${e.message}", null)
                }
            } else {
                result.error("PM_FAILED", "PM failed", null);
            }
            return
        }

        safeResult = MethodResultWrapper(result, channel!!)

        try {
            if (context != null) {
                PurchasingService.registerListener(context, purchasesUpdatedListener)
            } else {
                Log.w(
                    TAG,
                    "Cannot register listener on purchasing service because context is null."
                )
            }
        } catch (e: Exception) {
            Log.e(
                TAG,
                "For call '${call.method}', plugin failed to add purchase listener with error: ${e.message}"
            )
            safeResult!!.error(
                call.method,
                "Call endConnection method if you want to start over.",
                e.message
            )
        }

        when (call.method) {
            MethodNames.INITIALIZE -> {
                try {
                    LicensingService.verifyLicense(
                        context
                    ) {
                        Log.d(TAG, "License verification response ${it.requestStatus}")
                        safeResult?.invokeMethod(
                            MethodNames.LICENSE_VERIFICATION_RESPONSE_CALLBACK,
                            it.requestStatus.name
                        )
                    };
                    result.success(true);
                } catch (e: Exception) {
                    result.error("LICENSING_VERIFICATION_FAILED", "Failed verification ${e.message}", e.message);
                }
            }
            MethodNames.PLATFORM_VERSION -> {
                result.success("Android ${android.os.Build.VERSION.RELEASE}")
            }
            MethodNames.CLIENT_INFORMATION -> {
                val data = PurchasingService.getUserData();
                Log.d(TAG, "Requesting user data from purchasing service: ${data.toJSON()}");
                Log.d(TAG, "Appstore SDK Mode: " + LicensingService.getAppstoreSDKMode());
                result.success(true)
            }
            MethodNames.SDK_MODE -> {
                result.success(LicensingService.getAppstoreSDKMode())
            }
            "initConnection" -> {
                PurchasingService.getUserData()
                safeResult!!.success("Billing client ready")
            }
            "endConnection" -> {
                safeResult!!.success("Billing client has ended.")
            }
            "isReady" -> {
                safeResult!!.success(true)
            }
            "showInAppMessages" -> {
                safeResult!!.success("in app messages not supported for amazon")
            }
            "consumeAllItems" -> {
                // consumable is a separate type in amazon
                safeResult!!.success("no-ops in amazon")
            }
            "acknowledgePurchase" -> {
                safeResult!!.success("no-ops in amazon")
            }
            "getProducts",
            "getSubscriptions" -> {
                Log.d(TAG, call.method)
                val skus = call.argument<ArrayList<String>>("skus")!!
                val productSkus: MutableSet<String> = HashSet()
                for (i in skus.indices) {
                    Log.d(TAG, "Adding " + skus[i])
                    productSkus.add(skus[i])
                }
                PurchasingService.getProductData(productSkus)
            }
            "getAvailableItemsByType" -> {
                val type = call.argument<String>("type")
                Log.d(TAG, "gaibt=$type")
                // NOTE: getPurchaseUpdates doesnt return Consumables which are FULFILLED
                if (type == "inapp") {
                    PurchasingService.getPurchaseUpdates(true)
                } else if (type == "subs") {
                    // Subscriptions are retrieved during inapp, so we just return empty list
                    safeResult!!.success("[]")
                } else {
                    safeResult!!.notImplemented()
                }
            }
            "getPurchaseHistoryByType" -> {
                // No equivalent
                safeResult!!.success("[]")
            }
            "buyItemByType" -> {
                val type = call.argument<String>("type")
                //val obfuscatedAccountId = call.argument<String>("obfuscatedAccountId")
                //val obfuscatedProfileId = call.argument<String>("obfuscatedProfileId")
                val sku = call.argument<String>("sku")
                val oldSku = call.argument<String>("oldSku")
                //val prorationMode = call.argument<Int>("prorationMode")!!
                Log.d(TAG, "type=$type||sku=$sku||oldsku=$oldSku")
                val requestId = PurchasingService.purchase(sku)
                Log.d(TAG, "resid=$requestId")
            }
            "consumeProduct" -> {
                // consumable is a separate type in amazon
                safeResult!!.success("no-ops in amazon")
            }
            else -> {
                safeResult!!.notImplemented()
            }
        }
    }

    private val purchasesUpdatedListener: PurchasingListener = object : PurchasingListener {
        override fun onUserDataResponse(response: UserDataResponse) {
            Log.d(TAG, "Received user data response: $response")
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

                Log.d(TAG, "Putting data: $item")
                val result = safeResult
                if (result == null) {
                    Log.d(TAG, "Method result is null")
                } else {
                    result.invokeMethod(MethodNames.CLIENT_INFORMATION_CALLBACK, item)
                }
            } catch (e: Exception) {
                Log.e(TAG, "ON_USER_DATA_RESPONSE_JSON_PARSE_ERROR: ${e.message}")
                safeResult?.error("ON_USER_DATA_RESPONSE_JSON_PARSE_ERROR", e.message, null);
            }
        }

        // getItemsByType
        override fun onProductDataResponse(response: ProductDataResponse) {
            Log.d(TAG, "opdr=$response")
            val status = response.requestStatus
            Log.d(TAG, "onProductDataResponse: RequestStatus ($status)")
            when (status) {
                ProductDataResponse.RequestStatus.SUCCESSFUL -> {
                    Log.d(
                        TAG,
                        "onProductDataResponse: successful.  The item data map in this response includes the valid SKUs"
                    )
                    val productData = response.productData
                    //Log.d(TAG, "productData="+productData.toString());
                    val unavailableSkus = response.unavailableSkus
                    Log.d(
                        TAG,
                        "onProductDataResponse: " + unavailableSkus.size + " unavailable skus"
                    )
                    Log.d(TAG, "unavailableSkus=$unavailableSkus")
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
                            Log.d(TAG, "opdr Putting $item")
                            items.put(item)
                        }
                        //System.err.println("Sending "+items.toString());
                        safeResult!!.success(items.toString())
                    } catch (e: JSONException) {
                        safeResult!!.error(TAG, "E_BILLING_RESPONSE_JSON_PARSE_ERROR", e.message)
                    }
                }
                null,
                ProductDataResponse.RequestStatus.FAILED -> {
                    safeResult!!.error(TAG, "FAILED", null)
                    Log.d(TAG, "onProductDataResponse: failed, should retry request")
                    safeResult!!.error(TAG, "NOT_SUPPORTED", null)
                }
                ProductDataResponse.RequestStatus.NOT_SUPPORTED -> {
                    Log.d(TAG, "onProductDataResponse: failed, should retry request")
                    safeResult!!.error(TAG, "NOT_SUPPORTED", null)
                }
            }
        }

        // buyItemByType
        override fun onPurchaseResponse(response: PurchaseResponse) {
            Log.d(TAG, "opr=$response")
            when (val status = response.requestStatus) {
                PurchaseResponse.RequestStatus.SUCCESSFUL -> {
                    val receipt = response.receipt
                    PurchasingService.notifyFulfillment(
                        receipt.receiptId,
                        FulfillmentResult.FULFILLED
                    )
                    val date = receipt.purchaseDate
                    val transactionDate = date.time
                    try {
                        val item = getPurchaseData(
                            receipt.sku,
                            receipt.receiptId,
                            receipt.receiptId,
                            transactionDate.toDouble()
                        )
                        Log.d(TAG, "opr Putting $item")
                        safeResult!!.success(item.toString())
                        safeResult!!.invokeMethod("purchase-updated", item.toString())
                    } catch (e: JSONException) {
                        safeResult!!.error(TAG, "E_BILLING_RESPONSE_JSON_PARSE_ERROR", e.message)
                    }
                }
                PurchaseResponse.RequestStatus.FAILED -> safeResult!!.error(
                    TAG,
                    "buyItemByType",
                    "billingResponse is not ok: $status"
                )
                else -> {}
            }
        }

        // getAvailableItemsByType
        override fun onPurchaseUpdatesResponse(response: PurchaseUpdatesResponse) {
            Log.d(TAG, "opudr=$response")
            when (response.requestStatus) {
                PurchaseUpdatesResponse.RequestStatus.SUCCESSFUL -> {
                    val items = JSONArray()
                    try {
                        val receipts = response.receipts
                        for (receipt in receipts) {
                            val date = receipt.purchaseDate
                            val transactionDate = date.time
                            val item = getPurchaseData(
                                receipt.sku,
                                receipt.receiptId,
                                receipt.receiptId,
                                transactionDate.toDouble()
                            )
                            Log.d(TAG, "opudr Putting $item")
                            items.put(item)
                        }
                        safeResult!!.success(items.toString())
                    } catch (e: JSONException) {
                        safeResult!!.error(TAG, "E_BILLING_RESPONSE_JSON_PARSE_ERROR", e.message)
                    }
                }
                PurchaseUpdatesResponse.RequestStatus.FAILED -> safeResult!!.error(
                    TAG,
                    "FAILED",
                    null
                )
                PurchaseUpdatesResponse.RequestStatus.NOT_SUPPORTED -> {
                    Log.d(TAG, "onPurchaseUpdatesResponse: failed, should retry request")
                    safeResult!!.error(TAG, "NOT_SUPPORTED", null)
                }
            }
        }
    }

    @Throws(JSONException::class)
    fun getPurchaseData(
        productId: String?, transactionId: String?, transactionReceipt: String?,
        transactionDate: Double?
    ): JSONObject {
        val item = JSONObject()
        item.put("productId", productId)
        item.put("transactionId", transactionId)
        item.put("transactionReceipt", transactionReceipt)
        item.put("transactionDate", (transactionDate!!).toString())
        item.put("dataAndroid", null)
        item.put("signatureAndroid", null)
        item.put("purchaseToken", null)
        return item
    }
}