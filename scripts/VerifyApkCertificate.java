import com.android.apksig.ApkVerifier;
import java.io.File;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Structured result from verified certificates; never emits tool exceptions or subjects. */
public final class VerifyApkCertificate {
    private static String fingerprint(X509Certificate certificate) throws Exception {
        byte[] bytes = MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
        StringBuilder result = new StringBuilder(64);
        for (byte value : bytes) {
            result.append(Character.forDigit((value >>> 4) & 15, 16));
            result.append(Character.forDigit(value & 15, 16));
        }
        return result.toString();
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.exit(1);
        }
        try {
            ApkVerifier.Result result = new ApkVerifier.Builder(new File(args[0]))
                    .setMinCheckedPlatformVersion(26).build().verify();
            if (!result.isVerified()) {
                System.out.println("{\"verified\":false,\"signerCount\":0,\"sha256\":null,\"rotation\":false}");
                return;
            }
            List<X509Certificate> certificates = result.getSignerCertificates();
            Set<String> identities = new HashSet<>();
            for (X509Certificate certificate : certificates) {
                identities.add(fingerprint(certificate));
            }
            for (ApkVerifier.Result.V1SchemeSignerInfo signer : result.getV1SchemeSigners()) {
                identities.add(fingerprint(signer.getCertificate()));
            }
            for (ApkVerifier.Result.V2SchemeSignerInfo signer : result.getV2SchemeSigners()) {
                identities.add(fingerprint(signer.getCertificate()));
            }
            for (ApkVerifier.Result.V3SchemeSignerInfo signer : result.getV3SchemeSigners()) {
                identities.add(fingerprint(signer.getCertificate()));
            }
            for (ApkVerifier.Result.V3SchemeSignerInfo signer : result.getV31SchemeSigners()) {
                identities.add(fingerprint(signer.getCertificate()));
            }
            boolean rotation = identities.size() > 1 ||
                    (result.getSigningCertificateLineage() != null &&
                     result.getSigningCertificateLineage().getCertificatesInLineage().size() > 1);
            String sha = certificates.size() == 1 ?
                    "\"" + fingerprint(certificates.get(0)) + "\"" : "null";
            System.out.println("{\"verified\":true,\"signerCount\":" + certificates.size() +
                    ",\"sha256\":" + sha + ",\"rotation\":" + rotation + "}");
        } catch (com.android.apksig.apk.ApkFormatException error) {
            // An invalid APK cannot supply a verified signing certificate.
            System.out.println("{\"verified\":false,\"signerCount\":0,\"sha256\":null,\"rotation\":false}");
        } catch (Exception | LinkageError error) {
            // Do not expose SDK exceptions, certificate subjects, or private paths.
            System.exit(1);
        }
    }
}
