import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../app/widgets/primary_scaffold.dart';
import '../../../../core/utils/validators.dart';
import '../../../../core/widgets/section_card.dart';
import '../../data/room_repository.dart';

class CreateRoomScreen extends ConsumerStatefulWidget {
  const CreateRoomScreen({super.key, this.preselectedFixtureId});

  final String? preselectedFixtureId;

  @override
  ConsumerState<CreateRoomScreen> createState() => _CreateRoomScreenState();
}

class _CreateRoomScreenState extends ConsumerState<CreateRoomScreen> {
  final _formKey = GlobalKey<FormState>();
  final _nameController = TextEditingController();
  int _selectedSpots = 10;
  bool _loading = false;

  @override
  void dispose() {
    _nameController.dispose();
    super.dispose();
  }

  Future<void> _create() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() => _loading = true);

    try {
      final community = await ref.read(roomRepositoryProvider).createRoom(
            name: _nameController.text.trim(),
            maxMembers: _selectedSpots,
          );

      if (!mounted) return;

      ref.invalidate(myRoomsProvider);
      ref.invalidate(allRoomsProvider);

      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Community created successfully')),
      );

      context.go('/communities/${community.id}');
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(e.toString().replaceFirst('Exception: ', ''))),
      );
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return PrimaryScaffold(
      currentIndex: 1,
      title: 'Create Community',
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          const SectionCard(
            child: Text(
              'Create the community first, invite members, and then anyone inside the community can create contests for different matches.',
              style: TextStyle(height: 1.5),
            ),
          ),
          SectionCard(
            child: Form(
              key: _formKey,
              child: Column(
                children: [
                  TextFormField(
                    controller: _nameController,
                    decoration: const InputDecoration(
                      labelText: 'Community name',
                      prefixIcon: Icon(Icons.groups_rounded),
                    ),
                    validator: (value) => Validators.requiredField(
                      value,
                      label: 'Community name',
                    ),
                  ),
                  const SizedBox(height: 16),
                  DropdownButtonFormField<int>(
                    initialValue: _selectedSpots,
                    decoration: const InputDecoration(
                      labelText: 'Max members',
                      prefixIcon: Icon(Icons.people_alt_rounded),
                    ),
                    items: List<DropdownMenuItem<int>>.generate(29, (index) {
                      final value = index + 2;
                      return DropdownMenuItem<int>(
                        value: value,
                        child: Text('$value members'),
                      );
                    }),
                    onChanged: _loading
                        ? null
                        : (value) {
                            if (value != null) {
                              setState(() => _selectedSpots = value);
                            }
                          },
                  ),
                  const SizedBox(height: 24),
                  SizedBox(
                    width: double.infinity,
                    child: ElevatedButton(
                      onPressed: _loading ? null : _create,
                      child: _loading
                          ? const SizedBox(
                              height: 22,
                              width: 22,
                              child: CircularProgressIndicator(
                                strokeWidth: 2.2,
                                color: Colors.white,
                              ),
                            )
                          : const Text('Create Community'),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
