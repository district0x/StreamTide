(ns streamtide.shared.graphql-schema)

(def graphql-schema "
scalar Date

type Query {
    user(
        user_address: ID!
    ): User

    searchUsers(
        user_name: String
        user_address: String
        user_blacklisted: Boolean
        searchTerm: String
        orderBy: UsersOrderBy
        orderDir: OrderDir
        first: Int
        after: String
    ): UserList

    grant(
        user_address: ID!
    ): Grant

    searchGrants(
        searchTerm: String
        statuses: [GrantStatus!]
        orderBy: GrantsOrderBy
        orderDir: OrderDir
        first: Int
        after: String
    ): GrantList

    searchContents(
        user_address: ID
        onlyPublic: Boolean
        content_pinned: Boolean
        orderBy: ContentsOrderBy
        orderDir: OrderDir
        first: Int
        after: String
    ): ContentList

    searchDonations(
        sender: ID
        receiver: ID
        round: ID
        searchTerm: String
        orderBy: DonationsOrderBy
        orderDir: OrderDir
        first: Int
        after: String
    ): DonationList

    searchMatchings(
        receiver: ID
        round: ID
        searchTerm: String
        orderBy: MatchingsOrderBy
        orderDir: OrderDir
        first: Int
        after: String
    ): MatchingList

    searchLeaders(
        round: ID
        searchTerm: String
        orderBy: LeadersOrderBy
        orderDir: OrderDir
        first: Int
        after: String
    ): LeaderList

    round(
        round_id: ID
    ): Round

    searchRounds(
        orderBy: RoundOrderBy
        orderDir: OrderDir
        first: Int
        after: String
    ): RoundList

    roles: [Role]

    announcements(
        first: Int
        after: String
    ): AnnouncementList

}

type Mutation {
    updateUserInfo(
        input: UserInput
    ): User

    requestGrant
    : Boolean

    reviewGrants(
        user_addresses: [ID!]!
        grant_status: GrantStatus!
    ): Boolean

    addContent(
        content_type: ContentType!
        content_url: String!
        content_public: Boolean
        content_pinned: Boolean
    ): Boolean

    removeContent(
        content_id: ID!
    ): Boolean

    setContentVisibility(
        content_id: ID!
        content_public: Boolean!
    ): Boolean

    setContentPinned(
        content_id: ID!
        content_pinned: Boolean!
    ): Boolean

    generateLoginPayload(
        user_address: ID!
        chainId: String
    ): LoginPayload!

    signIn(
        signature: String!
        payload: LoginPayloadInput!
    ): signInPayload!

    blacklist(
        user_address: ID!
        blacklist: Boolean!
    ): User!

    addAnnouncement(
        announcement_text: String!
    ): Boolean

    removeAnnouncement(
        announcement_id: ID!
    ): Boolean

    verifySocial(
        code: String
        state: String!
    ): ValidationResult

    generateTwitterOauthUrl(
        callback: String!
    ): String!

    addNotificationType(
       notification_type: NotificationTypeSettingInput
    ): Boolean
}

input UserInput {
    user_name: String
    user_description: String
    user_tagline: String
    user_handle: String
    user_url: String
    user_photo: String
    user_bgPhoto: String
    user_perks: String
    user_minDonation: String
    user_socials: [SocialLinkInput!]
    user_notificationCategories: [NotificationCategorySettingInput!]
    user_notificationTypes: [NotificationTypeSettingInput!]
}

type LoginPayload {
    scheme: String
    domain: String!
    address: String!
    statement: String
    uri: String!
    version: String!
    chainId: String!
    nonce: String!
    issuedAt: String!
    expirationTime: String
    notBefore: String
    requestId: String
    resources: [String]
}

input LoginPayloadInput {
    scheme: String
    domain: String!
    address: String!
    statement: String
    uri: String!
    version: String!
    chainId: String!
    nonce: String!
    issuedAt: String!
    expirationTime: String
    notBefore: String
    requestId: String
    resources: [String]
}

type signInPayload {
    jwt: String!
    user_address: String!
}

type User {
    user_address: ID
    user_name: String
    user_description: String
    user_tagline: String
    user_handle: String
    user_url: String
    user_perks: String
    user_minDonation: String
    user_socials: [SocialLink!]
    user_photo: String
    user_bgPhoto: String
    user_grant: Grant
    user_blacklisted: Boolean
    user_creationDate: Date
    user_lastSeen: Date
    user_lastModification: Date
    user_hasPrivateContent: Boolean
    user_unlocked: Boolean
    user_notificationCategories: [NotificationCategorySetting!]
    user_notificationTypes: [NotificationTypeSetting!]
}

type SocialLink {
    social_network: SocialNetwork!
    social_url: String!
    social_verified: Boolean
}

input SocialLinkInput {
    social_network: SocialNetwork!
    social_url: String
}

type NotificationCategorySetting {
    notification_category: NotificationCategory
    notification_type: NotificationType
    notification_enable: Boolean
}

type NotificationTypeSetting {
    notification_type: NotificationType!
    notification_userIds: [ID!]!
}

input NotificationCategorySettingInput {
    notification_category: NotificationCategory!
    notification_type: NotificationType!
    notification_enable: Boolean!
}

enum NotificationCategory {
    notificationCategory_announcements
    notificationCategory_newsletter
    notificationCategory_grantStatus
    notificationCategory_donations
    notificationCategory_patronPublications
}

input NotificationTypeSettingInput {
    notification_type: NotificationType!
    notification_userIds: [ID!]!
}

enum NotificationType {
    notificationType_email
    notificationType_discord
    notificationType_webPush
    notificationType_web3Push
}

type Grant {
    grant_user: User!
    grant_status: GrantStatus!
    grant_requestDate: Date
    grant_decisionDate: Date
}

type GrantList {
    items: [Grant]
    totalCount: Int
    endCursor: String
    hasNextPage: Boolean
}

type UserList {
    items: [User]
    totalCount: Int
    endCursor: String
    hasNextPage: Boolean
}

type AnnouncementList {
    items: [Announcement]
    totalCount: Int
    endCursor: String
    hasNextPage: Boolean
}

type Announcement {
    announcement_id: ID!
    announcement_text: String!
}

type Content {
    content_id: ID!
    content_user: User!
    content_creationDate: Date
    content_public: Boolean
    content_pinned: Boolean
    content_type: ContentType!
    content_url: String!
}

type ContentList {
    items: [Content]
    totalCount: Int
    endCursor: String
    hasNextPage: Boolean
}

type Donation {
    donation_id: ID!
    donation_sender: User!
    donation_receiver: User!
    donation_date: Date
    donation_amount: String
    donation_coin: Coin
    donation_round: Round
}

type DonationList {
    items: [Donation]
    totalCount: Int
    endCursor: String
    hasNextPage: Boolean
}

type Matching {
    matching_id: ID!
    matching_receiver: User!
    matching_date: Date
    matching_amount: String
    matching_coin: Coin
    matching_round: Round
}

type MatchingList {
    items: [Matching]
    totalCount: Int
    endCursor: String
    hasNextPage: Boolean
}

type Leader {
    leader_receiver: User!
    leader_donationAmount: String
    leader_matchingAmounts: [CoinAmount]
    leader_totalAmounts: [CoinAmount]
}

type CoinAmount {
    coin: Coin
    amount: String
}

type LeaderList {
    items: [Leader]
    totalCount: Int
    endCursor: String
    hasNextPage: Boolean
}

type Round {
    round_id: ID!
    round_start: Date
    round_duration: Int
    round_matchingPools: [MatchingPool]
}

type MatchingPool {
    matchingPool_coin: Coin
    matchingPool_amount: String
    matchingPool_distributed: String
}

type Coin {
    coin_address: String!
    coin_name: String
    coin_symbol: String
    coin_decimals: Int
}

type RoundList {
    items: [Round]
    totalCount: Int
    endCursor: String
    hasNextPage: Boolean
}

type ValidationResult {
    isValid: Boolean!
    url: String
    message: String
}

enum ContentType {
    contentType_image
    contentType_video
    contentType_document
    contentType_text
    contentType_audio
    contentType_other
}

enum SocialNetwork {
    facebook
    twitter
    instagram
    linkedin
    pinterest
    patreon
    discord
    eth
}

enum GrantStatus {
    grant_status_unrequested
    grant_status_requested
    grant_status_approved
    grant_status_rejected
}

enum GrantsOrderBy {
    grants_orderBy_username
    grants_orderBy_requestDate
    grants_orderBy_decisionDate
}

enum UsersOrderBy {
    users_orderBy_username
    users_orderBy_address
    users_orderBy_creationDate
    users_orderBy_lastSeen
    users_orderBy_lastModification
}

enum BlacklistedOrderBy {
    blacklisted_orderBy_address
    blacklisted_orderBy_username
    blacklisted_orderBy_blacklistedDate
}

enum ContentsOrderBy {
    contents_orderBy_creationDate
}

enum DonationsOrderBy {
    donations_orderBy_username
    donations_orderBy_amount
    donations_orderBy_date
}

enum MatchingsOrderBy {
    matchings_orderBy_username
    matchings_orderBy_amount
    matchings_orderBy_date
}

enum LeadersOrderBy {
    leaders_orderBy_username
    leaders_orderBy_donationAmount
    leaders_orderBy_matchingAmount
    leaders_orderBy_totalAmount
}

enum RoundOrderBy {
    rounds_orderBy_id
    rounds_orderBy_date
}

enum Role {
    role_admin
    role_user
}

enum OrderDir {
    asc
    desc
}

"
)

