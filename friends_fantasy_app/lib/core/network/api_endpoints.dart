class ApiEndpoints {
  static const String authBase = '/api/v1/auth';

  static const String login = '$authBase/login/password';
  static const String sendOtp = '$authBase/send-otp';
  static const String verifyOtp = '$authBase/verify-otp';
  static const String register = '$authBase/register';
  static const String refresh = '$authBase/refresh';
  static const String logout = '$authBase/logout';
  static const String forgotPasswordRequest = '$authBase/password/forgot/request';
  static const String forgotPasswordConfirm = '$authBase/password/forgot/confirm';
  static const String changePassword = '$authBase/password/change';
  static const String changePasswordRequestOtp =
      '$authBase/password/change/request-otp';
  static const String changePasswordConfirmOtp =
      '$authBase/password/change/confirm-otp';

  static const String me = '/api/v1/me';
  static const String myWallet = '/api/v1/me/wallet';
  static const String myStats = '/api/v1/me/stats';
  static const String notifications = '/api/v1/notifications';
  static const String notificationUnreadCount =
      '/api/v1/notifications/unread-count';
  static const String notificationDeviceToken =
      '/api/v1/notifications/device-token';
  static String markNotificationRead(String notificationId) =>
      '/api/v1/notifications/$notificationId/read';
  static const String markAllNotificationsRead =
      '/api/v1/notifications/read-all';

  static const String upcomingFixtures = '/api/v1/cricket/fixtures/upcoming';

  // Community
  static const String communities = '/api/v1/communities';
  static const String myCommunities = '/api/v1/communities/my';
  static const String createCommunity = '/api/v1/communities';
  static const String joinCommunityByCode = '/api/v1/communities/join-by-code';
  static String communityDetail(String communityId) =>
      '/api/v1/communities/$communityId';
  static String deleteCommunity(String communityId) =>
      '/api/v1/communities/$communityId';
  static String updateCommunity(String communityId) =>
      '/api/v1/communities/$communityId';
  static String communityMembers(String communityId) =>
      '/api/v1/communities/$communityId/members';
  static String communityTeamView(String communityId, String teamId) =>
      '/api/v1/communities/$communityId/teams/$teamId';
  static String inviteToCommunity(String communityId) =>
      '/api/v1/communities/$communityId/invite';
  static String createCommunityContest(String communityId) =>
      '/api/v1/communities/$communityId/contests';
  static String inviteToCommunityContest(String communityId, String contestId) =>
      '/api/v1/communities/$communityId/contests/$contestId/invite';
  static String communityTeam(String communityId) =>
      '/api/v1/communities/$communityId/team';

  static const String myRooms = myCommunities;
  static const String createRoom = createCommunity;
  static const String joinRoomByCode = joinCommunityByCode;
  static String roomDetail(String roomId) => communityDetail(roomId);
  static String roomMembers(String roomId) => communityMembers(roomId);
  static String inviteToRoom(String roomId) => inviteToCommunity(roomId);

  // Community invitation
  static const String incomingCommunityInvitations =
      '/api/v1/community-invitations/incoming';
  static String acceptCommunityInvitation(String invitationId) =>
      '/api/v1/community-invitations/$invitationId/accept';
  static String declineCommunityInvitation(String invitationId) =>
      '/api/v1/community-invitations/$invitationId/decline';

  static const String incomingRoomInvitations = incomingCommunityInvitations;
  static String acceptRoomInvitation(String invitationId) =>
      acceptCommunityInvitation(invitationId);
  static String declineRoomInvitation(String invitationId) =>
      declineCommunityInvitation(invitationId);

  // Admin fixture / contest
  static const String adminAuthBase = '/api/v1/admin/auth';
  static const String adminLogin = '$adminAuthBase/login';
  static const String adminUpcomingFixtures = '/api/v1/admin/fixtures/upcoming';
  static const String adminSyncUpcomingFixtures =
      '/api/v1/admin/fixtures/sync-upcoming';
  static const String adminUsers = '/api/v1/admin/users';
  static String adminCreateContest(String fixtureId) =>
      '/api/v1/admin/fixtures/$fixtureId/contests';
  static String adminUpdateContest(String contestId) =>
      '/api/v1/admin/contests/$contestId';
  static String adminWalletCredit(String userId) =>
      '/api/v1/admin/wallet/credit/$userId';
  static String adminWalletDebit(String userId) =>
      '/api/v1/admin/wallet/debit/$userId';
  static String adminUserActivityHistory(String userId) =>
      '$adminUsers/$userId/activity-history';
  static String adminBlockUser(String userId) => '$adminUsers/$userId/block';
  static String adminUnblockUser(String userId) =>
      '$adminUsers/$userId/unblock';

  // Fixture / contest / team
  static String fixtureDetail(String fixtureId) =>
      '/api/v1/fixtures/$fixtureId';
  static String fixtureContests(String fixtureId) =>
      '/api/v1/fixtures/$fixtureId/contests';
  static String fixturePlayerPool(String fixtureId) =>
      '/api/v1/fixtures/$fixtureId/player-pool';

  static String myTeamsByFixture(String fixtureId) =>
      '/api/v1/fixtures/$fixtureId/teams/my';
  static String createTeam(String fixtureId) =>
      '/api/v1/fixtures/$fixtureId/teams';
  static String teamDetail(String teamId) => '/api/v1/teams/$teamId';
  static String updateTeam(String teamId) => '/api/v1/teams/$teamId';
  static String deleteTeam(String teamId) => '/api/v1/teams/$teamId';

  static String contestDetail(String contestId) =>
      '/api/v1/contests/$contestId';
  static String contestLeaderboard(String contestId) =>
      '/api/v1/contests/$contestId/leaderboard';
  static String contestTeamView(String contestId, String teamId) =>
      '/api/v1/contests/$contestId/teams/$teamId';
  static String joinContest(String contestId) =>
      '/api/v1/contests/$contestId/join';
  static String myContestEntries(String contestId) =>
      '/api/v1/contests/$contestId/my-entry';
  static const String myContestHistory = '/api/v1/contests/history/my';

  // Friend
  static const String friends = '/api/v1/friends';
  static const String sendFriendRequest = '/api/v1/friends/request';
  static const String incomingFriendRequests =
      '/api/v1/friends/requests/incoming';
  static const String outgoingFriendRequests =
      '/api/v1/friends/requests/outgoing';
  static String acceptFriendRequest(String requestId) =>
      '/api/v1/friends/requests/$requestId/accept';
  static String rejectFriendRequest(String requestId) =>
      '/api/v1/friends/requests/$requestId/reject';
  static String unfriend(String friendId) => '/api/v1/friends/$friendId';
  static String friendStats(String friendId) =>
      '/api/v1/friends/$friendId/stats';
  static String userSearch(String query) => '/api/v1/users/search?query=$query';
}
