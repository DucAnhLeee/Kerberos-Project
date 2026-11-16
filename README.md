# Kerberos Protocol Demonstration

This project is a desktop demonstration of a Kerberos-style authentication flow. It consists of three Java Swing applications: a client, a Key Distribution Center (KDC) that performs Authentication Service (AS) and Ticket-Granting Service (TGS) operations, and a protected service server.

The application is intended for learning and classroom demonstrations. It is a small, custom protocol model, not an interoperable implementation of Kerberos and not suitable for production or exposure to untrusted networks. See [Kerberos.md](Kerberos.md) for the protocol flow, message formats, implementation details, and limitations.

## Requirements

- JDK 21
- A graphical desktop environment for the Swing interfaces
- Network access between the three processes if they run on separate machines

No third-party runtime libraries are required. Gradle is included through the wrapper.

## Build and launch

Start each application in a separate terminal from the project root. On Linux or macOS:

```bash
./gradlew build
./gradlew runKdcAdmin
./gradlew runServiceServer
./gradlew runClient
```

On Windows, use `gradlew.bat` in place of `./gradlew`.

The KDC and service server open their listening sockets only after you select their **Start** controls. The default ports are `8888` for the KDC and `9999` for the service server. The client connects to `localhost` by default. For a multi-machine demonstration, enter the server machine's reachable IP address in the client and configure the service server with the KDC's reachable host and port. Allow the required TCP ports through the host firewall.

You can also launch a single form from an IDE by running its main class:

| Application | Main class | Gradle task |
|---|---|---|
| KDC administration | `org.ADuck.KDCAdminForm` | `runKdcAdmin` |
| Service server | `org.ADuck.ServiceServerForm` | `runServiceServer` |
| Client | `org.ADuck.ClientForm` | `runClient` |

## Demo credentials

The KDC starts with these in-memory demonstration entries:

| Type | Name | Secret |
|---|---|---|
| User | `an` | `123456` |
| User | `binh` | `abc123` |
| Service | `FileServer` | `FileServer-demo-key` |

The service server must be configured with the same service name and secret as the KDC. These defaults are public in the source and must not be treated as real credentials.

## Run an authentication session

1. Start the KDC and choose **Start**. Its default port is `8888`.
2. Start the service server and choose **Start**. Its default port is `9999`; confirm that its service name and secret match the KDC entry. Its KDC host and port should point to the running KDC.
3. In the client, enter the KDC and service-server host/port values and use the **Connect** controls to ping both endpoints.
4. Submit a user name and password to request a TGT from the AS.
5. Request a service ticket from the TGS. The current client requests the service named `FileServer`.
6. Access the service to send an AP request and complete the AP reply check.
7. After successful AP authentication, use the RSA tab to generate a key pair on the service server and request RSA encryption or decryption.

The client and server interfaces display ticket information and activity logs for the demonstration. Ticket and session data are held in process memory and are cleared when the relevant application exits.

## Desktop workflow

All three windows share a light dashboard layout with grouped settings, read-only tables, resizable panes and scrollable content. Action buttons use stable two-column rows with status labels on separate rows. Server windows scroll their settings and table panes independently rather than wrapping the entire split pane in an additional scroll area. Table headers support sorting; selecting a KDC user fills the account field without exposing the password. Deleting a user requires confirmation.

- **Khởi động / Dừng máy chủ** controls prevent duplicate starts and keep listener settings fixed while running. Shutdown closes client sockets and waits in the background for workers before allowing a restart. Bind errors remain visible in the status area.
- The client serializes network operations and temporarily locks controls while a request is pending. Configuration and account edits invalidate the current authentication context and require endpoint checks again. Connection status is tracked independently of colors.
- Ticket expiry disables dependent actions. Clearing tickets also clears RSA/replay state. Changing RSA key size requires key generation again; the default is 2048 bits.
- RSA input is checked against its UTF-8 byte limit and ciphertext must be valid Base64 of the selected key size.
- Activity logs retain at most 100,000 characters; historical ticket/request tables retain at most 1,000 rows. These are display limits, not persistence. Logs follow new entries only when already scrolled to the bottom.
- Stopping the service clears its sessions, keys and replay cache after outstanding workers finish. Closing one window does not exit the other windows.

Launch all three windows together with `./gradlew runSingle` (or `gradlew.bat runSingle`). No listener starts automatically.

### Regression checks

Run `./gradlew desktopRegressionTest`. The dependency-free regression runner checks input validation, read-only table updates on the Swing event thread, occupied ports, duplicate starts and restarts. When a graphical display is available it also opens and closes the three forms on temporary ports, exercises AS/TGS/AP/RSA, rejects replay and verifies ticket-expiry cleanup and independent window closure. Rendered form images are written to `build/reports/desktop/`. GUI coverage is explicitly skipped in headless environments. The check also runs as part of `./gradlew check` and `./gradlew build`.

The application remains a teaching demonstration, not a production Kerberos implementation.

## RSA demonstration

The service server generates an RSA key pair per client and key size (1024 or 2048 bits). The private key remains in the server process; the public key is returned to the client. The current implementation uses `RSA/ECB/PKCS1Padding` and accepts plaintext up to 117 bytes with a 1024-bit key or 245 bytes with a 2048-bit key. This is an application-level extension used to demonstrate an authenticated service request; it is not one of the six Kerberos messages.

RSA-1024 and PKCS#1 v1.5 encryption are included only for demonstration. Use modern, reviewed cryptographic protocols and appropriate key management for real systems.

## Project layout

```text
src/main/java/org/ADuck/
  ClientForm.java        Client interface and AS/TGS/AP/RSA requests
  KDCAdminForm.java      KDC interface, sample accounts, and ticket issuance
  ServiceServerForm.java Protected service, AP validation, and RSA operations
  CryptoUtil.java        AES-GCM encryption and key helpers
  GiaoThuc.java          Message and timestamp helpers
  RsaUtil.java           RSA key generation, encryption, and decryption
  DemoLauncher.java      Optional combined launcher
```

## Further documentation

- [Kerberos.md](Kerberos.md) describes the modeled protocol, message protection, runtime behavior, and known gaps.
