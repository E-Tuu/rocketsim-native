package org.roketsim.core.propulsion.catalog;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Original bytes, file revision/reference and parsed entries form one immutable source snapshot. */
public final class EngSourceFile {
    private final String fileName,revision,sourceReference,sha256;
    private final byte[] sourceBytes;
    private final List<EngMotorRecord> records;
    private final List<String> commentLines;

    EngSourceFile(String fileName,String revision,String reference,byte[] bytes,
            List<EngMotorRecord> records,List<String> comments) {
        this.fileName=EngMotorRecord.text(fileName,"fileName");
        this.revision=EngMotorRecord.text(revision,"revision");
        sourceReference=EngMotorRecord.text(reference,"sourceReference");
        sourceBytes=bytes.clone(); sha256=sha256(sourceBytes);
        this.records=List.copyOf(records); commentLines=List.copyOf(comments);
    }
    public String fileName() { return fileName; }
    public String revision() { return revision; }
    public String sourceReference() { return sourceReference; }
    public String sha256() { return sha256; }
    public byte[] sourceBytes() { return sourceBytes.clone(); }
    public List<EngMotorRecord> records() { return records; }
    public List<String> commentLines() { return commentLines; }
    static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch(NoSuchAlgorithmException e) { throw new IllegalStateException("JDK SHA-256 unavailable",e); }
    }
    @Override public boolean equals(Object other) {
        return other instanceof EngSourceFile source && fileName.equals(source.fileName) && revision.equals(source.revision)
            && sourceReference.equals(source.sourceReference) && Arrays.equals(sourceBytes,source.sourceBytes)
            && records.equals(source.records) && commentLines.equals(source.commentLines);
    }
    @Override public int hashCode() {
        return 31*Objects.hash(fileName,revision,sourceReference,records,commentLines)+Arrays.hashCode(sourceBytes);
    }
}
