import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';

import 'app/app.dart';
import 'core/notifications/app_firebase_options.dart';

@pragma('vm:entry-point')
Future<void> _firebaseMessagingBackgroundHandler(RemoteMessage message) async {
  final options = AppFirebaseOptions.currentPlatform;
  if (options == null) {
    return;
  }

  if (Firebase.apps.isEmpty) {
    await Firebase.initializeApp(options: options);
  }
}

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  final options = AppFirebaseOptions.currentPlatform;
  if (options != null) {
    await Firebase.initializeApp(options: options);
    FirebaseMessaging.onBackgroundMessage(_firebaseMessagingBackgroundHandler);
  }

  runApp(const ProviderScope(child: FantasyApp()));
}
