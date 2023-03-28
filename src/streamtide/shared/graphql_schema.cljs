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
        orderBy: ContentsOrderBy
        orderDir: OrderDir
        first: Int
        after: String
    ): ContentList

    searchDonations(
        sender: ID
        receiver: ID
        searchTerm: String
        orderBy: DonationsOrderBy
        orderDir: OrderDir
        first: Int
        after: String
    ): DonationList

#    searchBlacklisted(
#        orderBy: BlacklistedOrderBy
#        orderDir: OrderDir
#        first: Int
#        after: String
#    ): BlacklistedList

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

    reviewGrant(
        user_address: ID!
        grant_status: GrantStatus!
    ): Grant

    addContent(
        content_type: ContentType!
        content_url: String!
        content_public: Boolean
    ): Boolean

    removeContent(
        content_id: ID!
    ): Boolean

    setContentVisibility(
        content_id: ID!
        content_public: Boolean!
    ): Boolean

    signIn(
        dataSignature: String!
        data: String!
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

    verifyOauth(
        code: String!
        state: String!
    ): Boolean

    generateTwitterOauthUrl(
        callback: String!
    ): String!
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
    user_socials: [SocialLinkInput!]
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
    user_socials: [SocialLink!]
    user_photo: String
    user_bgPhoto: String
    user_grant: Grant
    user_blacklisted: Boolean
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

#type BlacklistedList {
#    items: [User]
#    totalCount: Int
#    endCursor: String
#    hasNextPage: Boolean
#}

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
    donation_sender: ID!
    donation_receiver: User!
    donation_date: Date
    donation_amount: Int
    donation_coin: ID
    donation_matching: Int
}

type DonationList {
    items: [Donation]
    totalCount: Int
    endCursor: String
    hasNextPage: Boolean
}

enum ContentType {
    contentType_image
    contentType_video
    contentType_document
    contentType_text
}

enum SocialNetwork {
    facebook
    twitter
    instagram
    linkedin
    pinterest
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
    donations_orderBy_grantedAmount
    donations_orderBy_matchingAmount
    donations_orderBy_totalAmount
    donations_orderBy_date
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

