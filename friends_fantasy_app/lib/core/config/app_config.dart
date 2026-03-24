import 'package:flutter/foundation.dart';

class AppConfig {
  static String get baseUrl {
    const envUrl = String.fromEnvironment('API_BASE_URL');
    if (envUrl.isNotEmpty) return envUrl;

    if (kIsWeb) {
      return 'http://localhost:8085/fantasybackend';
    }

    return 'http://10.0.2.2:8085/fantasybackend';
  }
}