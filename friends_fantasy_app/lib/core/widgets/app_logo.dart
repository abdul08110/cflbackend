import 'package:flutter/material.dart';

class AppLogo extends StatelessWidget {
  final double height;
  final BoxFit fit;

  const AppLogo({
    super.key,
    this.height = 90,
    this.fit = BoxFit.contain,
  });

  @override
  Widget build(BuildContext context) {
    return Image.asset(
      'assets/images/logo.png',
      height: height,
      fit: fit,
    );
  }
}