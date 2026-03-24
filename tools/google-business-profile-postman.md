# Google Business Profile API In Postman

This flow gets:

- `storeCode`
- `business_profile_id`
- `rating`
- `total_review_count`

For the API flow here, `business_profile_id` is the numeric `locationId`, which is the last path segment of `location.name`.

Example:

- `name = accounts/123456789/locations/779778951912526790`
- `business_profile_id = 779778951912526790`

## 1. Before You Start

You need:

- A Google Cloud project with Business Profile API access
- The API scope `https://www.googleapis.com/auth/business.manage`
- Postman OAuth 2.0 configured with your Google client ID and client secret

If your project quota is `0`, Google requires you to request Business Profile API access first.

## 2. Import The Collection

Import:

- `tools/google-business-profile-postman-collection.json`

## 3. Postman OAuth Setup

In Postman, for the collection or each request:

- Auth type: `OAuth 2.0`
- Grant type: `Authorization Code`
- Auth URL: `https://accounts.google.com/o/oauth2/v2/auth`
- Access Token URL: `https://oauth2.googleapis.com/token`
- Scope: `https://www.googleapis.com/auth/business.manage`

Use your Google Cloud OAuth client credentials.

## 4. Run The Requests

Run in this order:

1. `1. List Accounts`
2. `2. List Locations`
3. `3. Review Summary For One Location`

## 5. Bulk Export Pattern

### A. Get account ID

Run `1. List Accounts`.

The test script stores the first account into:

- `{{account_id}}`

If you have multiple accounts, pick the right one from the Postman console and set `account_id` manually.

### B. Get location list

Run `2. List Locations`.

In the Postman console, it prints rows like:

```json
{
  "accountId": "123456789",
  "locationName": "accounts/123456789/locations/779778951912526790",
  "locationId": "779778951912526790",
  "business_profile_id": "779778951912526790",
  "storeCode": "OM",
  "title": "Basics Life Ernakulam, Oberon Mall"
}
```

Copy those rows into a JSON file for the Collection Runner.

If Google returns `nextPageToken`, rerun the request with that token manually added as a query parameter and append those rows too.

### C. Get rating and review counts in bulk

Use Postman Collection Runner on:

- `3. Review Summary For One Location`

Use a data file with rows like:

```json
[
  {
    "storeCode": "OM",
    "locationId": "779778951912526790"
  },
  {
    "storeCode": "DG",
    "locationId": "123456789012345678"
  }
]
```

The request test script prints rows like:

```json
{
  "storeCode": "OM",
  "business_profile_id": "779778951912526790",
  "rating": 4.9,
  "total_review_count": 998
}
```

Copy the console output into Excel or a JSON/CSV file.

## 6. Important Note

The API does not return `rating` and `total_review_count` in the locations list itself. You get those from the reviews endpoint response fields:

- `averageRating`
- `totalReviewCount`

That is why the bulk flow is:

1. list accounts
2. list locations
3. call reviews for each location
