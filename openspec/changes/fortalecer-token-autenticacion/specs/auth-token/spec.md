# Spec Delta

## Purpose

Defines the externally observable contract for issuing and validating authentication tokens, including identity claims, client association, expiration, and protection of sensitive values.

## ADDED Requirements

### Requirement: Successful login tokens identify the authenticated user and client
The system SHALL issue a token for a successful login containing `sub`, `client`, `iat`, and `exp` claims; `sub` SHALL identify the authenticated user by stable user ID.

#### Scenario: Login issues the required claims
- **WHEN** a user submits valid credentials and a valid `client`
- **THEN** the returned token contains `sub` equal to that user's stable ID, `client` equal to the request value, and `iat` and `exp` timestamps

### Requirement: Login client is required and validated
The system SHALL reject login requests when `client` is missing, null, empty, or not a valid non-empty string, using HTTP 500 as decided for this change.

#### Scenario: Client is missing or invalid
- **WHEN** valid username and password are submitted with `client` missing, null, empty, or not a string
- **THEN** `POST /auth/login` returns HTTP 500

#### Scenario: Client is reflected without alteration
- **WHEN** a successful login request contains a valid `client` value
- **THEN** the token's `client` claim equals that request value

### Requirement: Token expiry agrees with the login response
The system SHALL set `expiresIn` to the token lifetime in seconds and ensure it differs from `exp - now` by no more than one second.

#### Scenario: Successful login reports a coherent expiry
- **WHEN** `POST /auth/login` returns a successful response
- **THEN** `expiresIn` and the token's `exp` claim represent the same lifetime within one second

### Requirement: Tokens can be validated and expired tokens are rejected
The system SHALL expose `POST /auth/validate`, accepting a JSON object with a `token` string, and SHALL distinguish valid tokens from invalid or expired tokens by HTTP status.

#### Scenario: Valid token is accepted
- **WHEN** a caller submits a valid, unexpired token to `POST /auth/validate`
- **THEN** the endpoint returns HTTP 200

#### Scenario: Expired token is rejected
- **WHEN** a caller submits a token whose `exp` has passed to `POST /auth/validate`
- **THEN** the endpoint returns HTTP 401

#### Scenario: Invalid token is rejected
- **WHEN** a caller submits a token that cannot be validated to `POST /auth/validate`
- **THEN** the endpoint returns HTTP 401

### Requirement: Sensitive authentication values are not logged
The system SHALL NOT write authentication tokens, secrets, or passwords to application logs during login or token validation.

#### Scenario: Authentication logs omit sensitive values
- **WHEN** a login or token validation request is processed
- **THEN** the resulting application logs contain no submitted password, token value, or authentication secret

### Requirement: A laboratory default secret is not used outside the sandbox
The system SHALL NOT use a laboratory default authentication secret outside the sandbox, and any such default retained for the sandbox SHALL be documented as laboratory-only.

#### Scenario: Non-sandbox operation has no laboratory default
- **WHEN** the service runs outside the sandbox
- **THEN** it does not authenticate tokens using a laboratory default secret

#### Scenario: Sandbox secret is documented
- **WHEN** a default authentication secret is present in the sandbox
- **THEN** its configuration documents that it is exclusively for laboratory use

### Requirement: Existing login behavior is preserved except where this change says otherwise
The system SHALL preserve the documented `POST /auth/login` response shape, credential validation behavior, and HTTP negotiation behavior except for the explicit `client` and token-expiry changes in this specification.

#### Scenario: Successful login preserves the response contract
- **WHEN** a caller submits valid credentials and a valid `client` without requiring prior authentication
- **THEN** the endpoint returns HTTP 200 JSON containing `token`, `expiresIn`, `user`, and `debug`, with `user.username`, `user.displayName`, and `user.role`, and does not create a session cookie

#### Scenario: Request and username validation precede password validation
- **WHEN** the request body is null, or username is missing, null, or blank
- **THEN** the endpoint returns HTTP 400 with the documented request or username error before evaluating password

#### Scenario: Password validation follows a valid username
- **WHEN** username is valid and password is missing, null, or blank
- **THEN** the endpoint returns HTTP 400 with the documented password error

#### Scenario: Invalid credentials remain unauthorized
- **WHEN** username and password are present but do not authenticate a user
- **THEN** the endpoint returns HTTP 401 with the documented invalid-credentials message

#### Scenario: Existing content negotiation remains unchanged
- **WHEN** a caller submits malformed JSON, an unsupported content type, or an incompatible `Accept` header
- **THEN** the request retains the documented framework responses: HTTP 400, 415, or 406 respectively, with the documented empty-body behavior