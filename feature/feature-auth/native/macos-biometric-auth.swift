import LocalAuthentication
import Foundation

// Exit codes:
// 0 = authentication success
// 1 = authentication failed / cancelled
// 2 = biometric/credential not available
// 3 = error

let reason = CommandLine.arguments.count > 1
    ? CommandLine.arguments[1]
    : "Verify your identity"

let context = LAContext()
var error: NSError?

// Check if device owner authentication is available (biometric + passcode)
guard context.canEvaluatePolicy(.deviceOwnerAuthentication, error: &error) else {
    exit(2) // not available
}

let semaphore = DispatchSemaphore(value: 0)
var exitCode: Int32 = 3

context.evaluatePolicy(
    .deviceOwnerAuthentication,
    localizedReason: reason
) { success, evaluateError in
    if success {
        exitCode = 0
    } else if let nsError = evaluateError as NSError? {
        switch nsError.code {
        case -2, -4: // userCancel, systemCancel
            exitCode = 1
        case -3: // userFallback
            exitCode = 1
        default:
            exitCode = 3
        }
    } else {
        exitCode = 3
    }
    semaphore.signal()
}

semaphore.wait()
exit(exitCode)
