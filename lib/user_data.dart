import 'package:json_annotation/json_annotation.dart';

part 'user_data.g.dart';

@JsonSerializable()
class AmazonUserData {
  final String? marketplace;
  final String? userId;
  final String? status;

  const AmazonUserData(this.marketplace, this.userId, this.status);

  factory AmazonUserData.fromJson(dynamic data) {
    final Map<Object?, Object?> value = data;
    final json = <String, dynamic>{};
    for (var i = 0; i < value.length; i++) {
      final k = value.entries.elementAt(i).key;
      json[k?.toString() ?? ''] = value.entries.elementAt(i).value;
    }
    return _$AmazonUserDataFromJson(json);
  }

  Map<String, dynamic> toJson() => _$AmazonUserDataToJson(this);

  @override
  String toString() {
    return 'AmazonUserData{marketplace: $marketplace, userId: $userId, status: $status}';
  }
}

class PurchasedItemExtraData {
  const PurchasedItemExtraData();
}

@JsonSerializable()
class PurchasedItemExtra {
  final AmazonPurchasedItemExtraData? amazon;

  const PurchasedItemExtra(this.amazon);

  factory PurchasedItemExtra.fromJson(dynamic json) =>
      _$PurchasedItemExtraFromJson(json ?? const <String, dynamic>{});

  Map<String, dynamic> toJson() => _$PurchasedItemExtraToJson(this);
}

@JsonSerializable()
class AmazonPurchasedItemExtraData extends PurchasedItemExtraData {
  final String? productType;
  final String? cancelDate;
  final String? deferredDate;
  final String? deferredSku;
  final String? termSku;

  const AmazonPurchasedItemExtraData(
    this.productType,
    this.cancelDate,
    this.deferredDate,
    this.deferredSku,
    this.termSku,
  );

  factory AmazonPurchasedItemExtraData.fromJson(dynamic json) =>
      _$AmazonPurchasedItemExtraDataFromJson(json);

  Map<String, dynamic> toJson() => _$AmazonPurchasedItemExtraDataToJson(this);
}
