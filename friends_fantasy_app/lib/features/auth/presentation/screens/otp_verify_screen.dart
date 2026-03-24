import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:pinput/pinput.dart';

import '../../../../app/theme/app_theme.dart';
import '../providers/auth_controller.dart';
import '../../../../core/widgets/app_logo.dart';

class OtpVerifyScreen extends ConsumerStatefulWidget {
  const OtpVerifyScreen({super.key});

  @override
  ConsumerState<OtpVerifyScreen> createState() => _OtpVerifyScreenState();
}

class _OtpVerifyScreenState extends ConsumerState<OtpVerifyScreen> {
  final _otpController = TextEditingController();

  Timer? _timer;
  int _secondsLeft = 30;
  int _resendCount = 0;
  static const int _maxResends = 5;

  @override
  void initState() {
    super.initState();
    _startTimer();
  }

  void _startTimer() {
    _timer?.cancel();
    setState(() => _secondsLeft = 30);

    _timer = Timer.periodic(const Duration(seconds: 1), (timer) {
      if (_secondsLeft <= 1) {
        timer.cancel();
        setState(() => _secondsLeft = 0);
      } else {
        setState(() => _secondsLeft--);
      }
    });
  }

  @override
  void dispose() {
    _timer?.cancel();
    _otpController.dispose();
    super.dispose();
  }

  Future<void> _verify() async {
    FocusScope.of(context).unfocus();

    if (_otpController.text.trim().length != 6) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('Enter valid 6-digit OTP')));
      return;
    }

    final ok = await ref
        .read(authControllerProvider.notifier)
        .verifyOtpAndRegister(_otpController.text.trim());

    if (ok && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Registration successful. Please login.')),
      );
      context.go('/login');
    }
  }

  Future<void> _resendOtp() async {
    if (_secondsLeft > 0) return;

    if (_resendCount >= _maxResends) {
      await showDialog(
        context: context,
        builder: (_) => AlertDialog(
          title: const Text('Resend limit reached'),
          content: const Text(
            'You have exceeded 5 OTP resend attempts. Please contact support. Registration is blocked for 24 hours.',
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('OK'),
            ),
          ],
        ),
      );
      return;
    }

    await ref.read(authControllerProvider.notifier).resendOtp();

    if (!mounted) return;

    setState(() {
      _resendCount++;
    });

    _startTimer();

    final error = ref.read(authControllerProvider).errorMessage;
    if (error == null) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('OTP resent successfully')));
    }
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(authControllerProvider);
    final draft = state.pendingRegister;

    if (draft == null) {
      return Scaffold(
        appBar: AppBar(),
        body: const Center(
          child: Text('Registration session expired. Please register again.'),
        ),
      );
    }

    final defaultPinTheme = PinTheme(
      width: 54,
      height: 58,
      textStyle: const TextStyle(
        fontSize: 22,
        color: Colors.white,
        fontWeight: FontWeight.w700,
      ),
      decoration: BoxDecoration(
        color: const Color(0xFF11182A),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AppTheme.darkStroke),
      ),
    );

    return Scaffold(
      appBar: AppBar(),
      body: Container(
        width: double.infinity,
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            colors: [Color(0xFF090E1A), Color(0xFF10182B), Color(0xFF1A1037)],
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
          ),
        ),
        child: SafeArea(
          top: false,
          child: Padding(
            padding: const EdgeInsets.fromLTRB(20, 8, 20, 24),
            child: Column(
              children: [
                const SizedBox(height: 24),
                const AppLogo(height: 80),
                const SizedBox(height: 16),
                const Text(
                  'Verify OTP',
                  style: TextStyle(
                    fontSize: 28,
                    fontWeight: FontWeight.w800,
                    color: Colors.white,
                  ),
                ),
                const SizedBox(height: 8),
                Text(
                  'OTP sent to ${draft.email}',
                  style: const TextStyle(
                    color: AppTheme.textSecondary,
                    fontSize: 14,
                  ),
                ),
                const SizedBox(height: 20),
                Pinput(
                  controller: _otpController,
                  length: 6,
                  defaultPinTheme: defaultPinTheme,
                  focusedPinTheme: defaultPinTheme.copyDecorationWith(
                    border: Border.all(color: AppTheme.primary, width: 1.4),
                  ),
                ),
                const SizedBox(height: 16),
                if (state.errorMessage != null)
                  Container(
                    width: double.infinity,
                    padding: const EdgeInsets.all(12),
                    margin: const EdgeInsets.only(bottom: 16),
                    decoration: BoxDecoration(
                      color: Colors.redAccent.withValues(alpha: 0.12),
                      borderRadius: BorderRadius.circular(14),
                      border: Border.all(
                        color: Colors.redAccent.withValues(alpha: 0.35),
                      ),
                    ),
                    child: Text(
                      state.errorMessage!,
                      style: const TextStyle(
                        color: Colors.redAccent,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),
                ElevatedButton(
                  onPressed: state.isBusy ? null : _verify,
                  child: state.isBusy
                      ? const SizedBox(
                          width: 22,
                          height: 22,
                          child: CircularProgressIndicator(
                            strokeWidth: 2.2,
                            color: Colors.white,
                          ),
                        )
                      : const Text('Verify OTP'),
                ),
                const SizedBox(height: 14),
                Text(
                  _secondsLeft > 0
                      ? 'Resend OTP in $_secondsLeft sec'
                      : 'Didn’t receive OTP?',
                  style: const TextStyle(color: AppTheme.textSecondary),
                ),
                TextButton(
                  onPressed: (_secondsLeft == 0 && !state.isBusy)
                      ? _resendOtp
                      : null,
                  child: Text(
                    _resendCount >= _maxResends
                        ? 'Resend blocked'
                        : 'Resend OTP',
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
