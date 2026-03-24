import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../app/theme/app_theme.dart';
import '../../../../core/utils/validators.dart';
import '../providers/auth_controller.dart';
import '../../../../core/widgets/app_logo.dart';

class LoginScreen extends ConsumerStatefulWidget {
  const LoginScreen({super.key});

  @override
  ConsumerState<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends ConsumerState<LoginScreen> {
  final _formKey = GlobalKey<FormState>();
  final _credentialController = TextEditingController();
  final _passwordController = TextEditingController();
  bool _obscure = true;

  @override
  void dispose() {
    _credentialController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    FocusScope.of(context).unfocus();
    if (!_formKey.currentState!.validate()) return;
    final ok = await ref.read(authControllerProvider.notifier).login(
      credential: _credentialController.text.trim(),
      password: _passwordController.text.trim(),
    );
    if (ok && mounted) context.go('/home');
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(authControllerProvider);

    return Scaffold(
      body: Container(
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            colors: [Color(0xFF090E1A), Color(0xFF10182B), Color(0xFF1A1037)],
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
          ),
        ),
        child: SafeArea(
          child: SingleChildScrollView(
            padding: const EdgeInsets.fromLTRB(20, 24, 20, 24),
            child: Column(
              children: [
                const SizedBox(height: 28),
                const AppLogo(height: 90),
                const SizedBox(height: 14),
                const Text('Welcome back', style: TextStyle(fontSize: 30, fontWeight: FontWeight.w800, color: Colors.white)),
                const SizedBox(height: 8),
                const Text(
                  'Login to create teams, join contests, and climb the leaderboard.',
                  textAlign: TextAlign.center,
                  style: TextStyle(color: AppTheme.textSecondary, fontSize: 14, height: 1.5),
                ),
                const SizedBox(height: 28),
                Card(
                  child: Padding(
                    padding: const EdgeInsets.all(18),
                    child: Form(
                      key: _formKey,
                      child: Column(
                        children: [
                          TextFormField(
                            controller: _credentialController,
                            decoration: const InputDecoration(
                              labelText: 'Username, mobile, or email',
                              prefixIcon: Icon(Icons.person_outline_rounded),
                            ),
                            validator: (value) => Validators.requiredField(
                              value,
                              label: 'Username, mobile, or email',
                            ),
                          ),
                          const SizedBox(height: 16),
                          TextFormField(
                            controller: _passwordController,
                            obscureText: _obscure,
                            decoration: InputDecoration(
                              labelText: 'Password',
                              prefixIcon: const Icon(Icons.lock_outline_rounded),
                              suffixIcon: IconButton(
                                onPressed: () => setState(() => _obscure = !_obscure),
                                icon: Icon(_obscure ? Icons.visibility_off_outlined : Icons.visibility_outlined),
                              ),
                            ),
                            validator: Validators.password,
                            onFieldSubmitted: (_) => _submit(),
                          ),
                          if (state.errorMessage != null) ...[
                            const SizedBox(height: 12),
                            Container(
                              width: double.infinity,
                              padding: const EdgeInsets.all(12),
                              decoration: BoxDecoration(
                                color: Colors.redAccent.withValues(alpha: 0.12),
                                borderRadius: BorderRadius.circular(14),
                                border: Border.all(color: Colors.redAccent.withValues(alpha: 0.35)),
                              ),
                              child: Text(state.errorMessage!, style: const TextStyle(color: Colors.redAccent, fontWeight: FontWeight.w600)),
                            ),
                          ],
                          const SizedBox(height: 16),
                          Align(
                            alignment: Alignment.centerRight,
                            child: TextButton(
                              onPressed: state.isBusy
                                  ? null
                                  : () => context.push('/forgot-password'),
                              child: const Text(
                                'Forgot password?',
                                textAlign: TextAlign.center,
                              ),
                            ),
                          ),
                          const SizedBox(height: 4),
                          ElevatedButton(
                            onPressed: state.isBusy ? null : _submit,
                            child: state.isBusy
                                ? const SizedBox(height: 22, width: 22, child: CircularProgressIndicator(strokeWidth: 2.2, color: Colors.white))
                                : const Text('Login', textAlign: TextAlign.center),
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
                const SizedBox(height: 10),
                OutlinedButton.icon(
                  onPressed: () => context.go('/admin/login'),
                  icon: const Icon(Icons.admin_panel_settings_outlined),
                  label: const Text('Admin Login', textAlign: TextAlign.center),
                ),
                const SizedBox(height: 16),
                Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    const Text('New here?', style: TextStyle(color: AppTheme.textSecondary)),
                    TextButton(
                      onPressed: () => context.push('/register'),
                      child: const Text('Create account', textAlign: TextAlign.center),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
