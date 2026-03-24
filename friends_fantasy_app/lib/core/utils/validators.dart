class Validators {
  static String? requiredField(String? value, {String label = 'Field'}) {
    if (value == null || value.trim().isEmpty) return '$label is required';
    return null;
  }

  static String? mobile(String? value) {
    final raw = value?.trim() ?? '';
    if (raw.isEmpty) return 'Mobile number is required';
    if (!RegExp(r'^\d{10}$').hasMatch(raw)) return 'Enter a valid 10-digit mobile number';
    return null;
  }

  static String? username(String? value) {
    final raw = value?.trim() ?? '';
    if (raw.isEmpty) return 'Username is required';
    if (raw.length < 4) return 'Username must be at least 4 characters';
    if (!RegExp(r'^[a-zA-Z0-9_]+$').hasMatch(raw)) return 'Only letters, numbers, and underscore allowed';
    return null;
  }

  static String? email(String? value) {
    final raw = value?.trim() ?? '';
    if (raw.isEmpty) return 'Email is required';
    if (!RegExp(r'^[^@]+@[^@]+\.[^@]+$').hasMatch(raw)) return 'Enter a valid email';
    return null;
  }

  static String? password(String? value) {
    final raw = value?.trim() ?? '';
    if (raw.isEmpty) return 'Password is required';
    if (raw.length < 6) return 'Password must be at least 6 characters';
    return null;
  }

  static String? confirmPassword(String? value, String original) {
    final current = value?.trim() ?? '';
    if (current.isEmpty) return 'Confirm your password';
    if (current != original.trim()) return 'Passwords do not match';
    return null;
  }
}
