import Foundation
import CryptoKit
import Security

struct LoginGrant {
    let id: String
    let verifier: String
    let pairing: String
    let deadline: Date
}

enum AppFailure: LocalizedError {
    case message(String)
    var errorDescription: String? { if case let .message(message) = self { return message }; return nil }
}

struct NativeReply: Decodable, Sendable {
    let id: String?
    let ready: Bool?
}

struct NativeLoginClient {
    static let origin = URL(string: "https://edijs-calisthenics.groovycocoa1.chatgpt.site")!
    let userAgent: String
    private let session: URLSession
    init(userAgent: String) {
        self.userAgent = userAgent
        let config = URLSessionConfiguration.ephemeral
        config.httpCookieStorage = nil
        config.httpShouldSetCookies = false
        config.timeoutIntervalForRequest = 20
        session = URLSession(configuration: config)
    }
    func post(_ action: String, fields: [String: String]) async throws -> NativeReply {
        var request = URLRequest(url: Self.origin.appendingPathComponent("auth/native/" + action))
        request.httpMethod = "POST"
        request.setValue(userAgent, forHTTPHeaderField: "User-Agent")
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        // These fields are restricted to hex digests or decimal pairing codes.
        request.httpBody = fields.sorted(by: { $0.key < $1.key }).map { "\($0.key)=\($0.value)" }.joined(separator: "&").data(using: .utf8)
        let (data, response) = try await session.data(for: request)
        guard let response = response as? HTTPURLResponse, response.statusCode == 200 else {
            throw AppFailure.message("Pieslēgšanās neizdevās. Sāc vēlreiz. / Sign-in failed. Start again.")
        }
        return try JSONDecoder().decode(NativeReply.self, from: data)
    }
    func start() async throws -> LoginGrant {
        var random = [UInt8](repeating: 0, count: 32)
        guard SecRandomCopyBytes(kSecRandomDefault, random.count, &random) == errSecSuccess else {
            throw AppFailure.message("Neizdevās sākt pieslēgšanos. / Unable to start sign-in.")
        }
        let verifier = random.map { String(format: "%02x", $0) }.joined()
        let challenge = SHA256.hash(data: Data(verifier.utf8)).map { String(format: "%02x", $0) }.joined()
        let pairing = String(format: "%06d", Int.random(in: 0...999999))
        let result = try await post("start", fields: ["challenge": challenge, "pairing": pairing])
        guard let id = result.id, id.range(of: "^[a-f0-9]{64}$", options: .regularExpression) != nil else {
            throw AppFailure.message("Pieslēgšanās atbilde nav derīga. / Invalid sign-in response.")
        }
        return LoginGrant(id: id, verifier: verifier, pairing: pairing, deadline: Date().addingTimeInterval(300))
    }
    func ready(_ grant: LoginGrant) async throws -> Bool {
        let result = try await post("status", fields: ["id": grant.id, "verifier": grant.verifier])
        return result.ready == true
    }
    static func googleURL(_ grant: LoginGrant) -> URL {
        var components = URLComponents(url: origin.appendingPathComponent("auth/google"), resolvingAgainstBaseURL: false)!
        components.queryItems = [URLQueryItem(name: "next", value: "/auth/native/complete?id=" + grant.id)]
        return components.url!
    }
    static func exchangeRequest(_ grant: LoginGrant) -> URLRequest {
        var request = URLRequest(url: origin.appendingPathComponent("auth/native/exchange"))
        request.httpMethod = "POST"
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        request.httpBody = Data("id=\(grant.id)&verifier=\(grant.verifier)".utf8)
        return request
    }
}
