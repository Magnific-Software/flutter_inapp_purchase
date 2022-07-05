// ignore_for_file: constant_identifier_names

import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter_inapp_purchase/modules.dart';

import 'in_app_purchase_amazon_platform_interface.dart';
import 'user_data.dart';
import 'utils.dart';

class _MethodNames {
  static const UPDATE_PACKAGE_INSTALLER = "Additional#updatePackageInstaller()";
  static const INITIALIZE = "AmazonIAPClient#initialize()";
  static const PLATFORM_VERSION = "AmazonIAPClient#getPlatformVersion()";
  static const CLIENT_INFORMATION = "AmazonIAPClient#getClientInformation()";
  static const CLIENT_INFORMATION_CALLBACK =
      "AmazonIAPClient#onClientInformation(AmazonUserData)";
  static const SKUS_CALLBACK = "AmazonIAPClient#onSKU(AmazonUserData)";
  static const PURCHASE_UPDATE_CALLBACK =
      "AmazonIAPClient#onPurchaseUpdate(AmazonUserData)";
  static const SDK_MODE = "AmazonIAPClient#getSDKMode()";
  static const LICENSE_VERIFICATION_RESPONSE_CALLBACK =
      "AmazonIAPClient#onLicenseVerificationResponse()";
}

/// An implementation of [InAppPurchaseAmazonPlatform] that uses method channels.
class MethodChannelInAppPurchaseAmazon extends InAppPurchaseAmazonPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('flutter_inapp');

  Completer<void>? _completer;

  @override
  Future<bool?> updatePackageInstaller(String installerPackageName) {
    return methodChannel.invokeMethod<bool>(
      _MethodNames.UPDATE_PACKAGE_INSTALLER,
      installerPackageName,
    );
  }

  @override
  Future<void> initialize() async {
    if (_completer != null) return _completer!.future;

    _attachMethodChannelListeners();

    final completer = _completer ?? Completer();
    _completer = completer;

    if (!completer.isCompleted) {
      try {
        await methodChannel.invokeMethod(_MethodNames.INITIALIZE);
        completer.complete();
      } on PlatformException catch (e) {
        completer.completeError(e);
        _completer = null;
      }
    }

    return completer.future;
  }

  @override
  Future<String?> getPlatformVersion() {
    return methodChannel.invokeMethod<String>(
      _MethodNames.PLATFORM_VERSION,
    );
  }

  @override
  Future<String?> getAmazonSdkMode() {
    return methodChannel.invokeMethod<String>(
      _MethodNames.SDK_MODE,
    );
  }

  @override
  Future<bool> getClientInformation() async {
    final data = await methodChannel.invokeMethod<Object>(
      _MethodNames.CLIENT_INFORMATION,
    );
    return data == true;
  }

  void _attachMethodChannelListeners() {
    _clientInformationStreamController ??= StreamController.broadcast();
    _licenseVerificationResponseStreamController ??=
        StreamController.broadcast();
    _skusStreamController ??= StreamController.broadcast();
    _purchasedItemStreamController ??= StreamController.broadcast();
    return methodChannel.setMethodCallHandler(_onMethodCall);
  }

  StreamController<AmazonUserData?>? _clientInformationStreamController;
  StreamController<String?>? _licenseVerificationResponseStreamController;
  StreamController<List<IAPItem>>? _skusStreamController;
  StreamController<List<PurchasedItem>?>? _purchasedItemStreamController;

  @override
  Stream<AmazonUserData?> get clientInformationStream =>
      _clientInformationStreamController!.stream;

  @override
  Stream<String?> get licenseVerificationResponseStream =>
      _licenseVerificationResponseStreamController!.stream;

  @override
  Stream<List<IAPItem>> get skusStream => _skusStreamController!.stream;

  @override
  Stream<List<PurchasedItem>?> get purchasedItemStream =>
      _purchasedItemStreamController!.stream;

  Future<Object?> _onMethodCall(MethodCall call) async {
    if (kDebugMode) {
      debugPrint('onMethodCall(${call.method}, ${call.arguments})');
    }

    switch (call.method) {
      case _MethodNames.CLIENT_INFORMATION_CALLBACK:
        final Object? value = call.arguments;
        _clientInformationStreamController
            ?.add(value != null ? AmazonUserData.fromJson(value) : null);
        break;
      case _MethodNames.LICENSE_VERIFICATION_RESPONSE_CALLBACK:
        final Object? value = call.arguments;
        _licenseVerificationResponseStreamController
            ?.add(value is String ? value : null);
        break;
      case _MethodNames.SKUS_CALLBACK:
        final Object? value = call.arguments;
        try {
          final items = extractItems(value);
          _skusStreamController?.add(items);
        } catch (e, s) {
          _skusStreamController?.addError(
            Exception('${e.toString()}: $value\n'),
            s,
          );
        }
        break;
      case _MethodNames.PURCHASE_UPDATE_CALLBACK:
        final Object? value = call.arguments;
        try {
          final items = extractPurchased(value);
          _purchasedItemStreamController?.add(items);
        } catch (e, s) {
          _purchasedItemStreamController?.addError(
            Exception('${e.toString()}: $value\n'),
            s,
          );
        }
        break;
      default:
        final e = ArgumentError('Unknown method ${call.method}');
        final controller = _clientInformationStreamController;
        if (controller != null) {
          controller.addError(e, StackTrace.current);
        } else {
          throw e;
        }
    }
    return Future.value(null);
  }

  @override
  void dispose() {
    super.dispose();
    _clientInformationStreamController?.close();
    _licenseVerificationResponseStreamController?.close();
    _skusStreamController?.close();
    _purchasedItemStreamController?.close();
    _clientInformationStreamController = null;
    _licenseVerificationResponseStreamController = null;
    _skusStreamController = null;
    _purchasedItemStreamController = null;
    _completer = null;
  }
}
