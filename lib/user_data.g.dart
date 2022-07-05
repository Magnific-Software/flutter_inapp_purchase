// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'user_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

AmazonUserData _$AmazonUserDataFromJson(Map<String, dynamic> json) =>
    AmazonUserData(
      json['marketplace'] as String?,
      json['userId'] as String?,
      json['status'] as String?,
    );

Map<String, dynamic> _$AmazonUserDataToJson(AmazonUserData instance) =>
    <String, dynamic>{
      'marketplace': instance.marketplace,
      'userId': instance.userId,
      'status': instance.status,
    };

PurchasedItemExtra _$PurchasedItemExtraFromJson(Map<String, dynamic> json) =>
    PurchasedItemExtra(
      json['amazon'] == null
          ? null
          : AmazonPurchasedItemExtraData.fromJson(json['amazon']),
    );

Map<String, dynamic> _$PurchasedItemExtraToJson(PurchasedItemExtra instance) =>
    <String, dynamic>{
      'amazon': instance.amazon,
    };

AmazonPurchasedItemExtraData _$AmazonPurchasedItemExtraDataFromJson(
        Map<String, dynamic> json) =>
    AmazonPurchasedItemExtraData(
      json['productType'] as String?,
      json['cancelDate'] as String?,
      json['deferredDate'] as String?,
      json['deferredSku'] as String?,
      json['termSku'] as String?,
    );

Map<String, dynamic> _$AmazonPurchasedItemExtraDataToJson(
        AmazonPurchasedItemExtraData instance) =>
    <String, dynamic>{
      'productType': instance.productType,
      'cancelDate': instance.cancelDate,
      'deferredDate': instance.deferredDate,
      'deferredSku': instance.deferredSku,
      'termSku': instance.termSku,
    };
