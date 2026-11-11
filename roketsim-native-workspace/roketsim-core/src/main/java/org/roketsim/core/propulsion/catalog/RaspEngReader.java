package org.roketsim.core.propulsion.catalog;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import org.roketsim.core.propulsion.ThrustSample;

/** Offline RASP interchange adapter. Numeric text parsing never enters flight physics APIs. */
public final class RaspEngReader {
    public EngSourceFile read(String fileName,String revision,String sourceReference,byte[] bytes) {
        if(bytes==null) throw new IllegalArgumentException("sourceBytes must not be null");
        byte[] snapshot=bytes.clone();
        String text;
        try {
            text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(snapshot)).toString();
        } catch(CharacterCodingException e) { throw new IllegalArgumentException(fileName+": invalid UTF-8/ASCII",e); }
        // A BOM is transport metadata; it remains present in sourceBytes/checksum.
        if(text.startsWith("\ufeff")) text=text.substring(1);
        var records=new ArrayList<EngMotorRecord>(); var comments=new ArrayList<String>();
        EngMotorRecord.Header header=null;
        var samples=new ArrayList<ThrustSample>(); int headerLine=0; boolean separated=true;
        String[] lines=text.split("\\r\\n|\\n|\\r",-1);
        for(int i=0;i<lines.length;i++) {
            String line=lines[i].strip();
            if(line.isEmpty() || line.startsWith(";")) {
                if(line.startsWith(";")) comments.add(lines[i]);
                separated=true; continue;
            }
            String[] tokens=line.split("[ \\t]+");
            try {
                if(tokens.length==7) {
                    if(header!=null) {
                        if(!separated) throw new IllegalArgumentException("motor entries require a blank/comment separator");
                        records.add(new EngMotorRecord(header,samples,headerLine));
                    }
                    header=new EngMotorRecord.Header(Arrays.asList(tokens)); headerLine=i+1;
                    samples=new ArrayList<>();
                } else if(tokens.length==2 && header!=null) {
                    samples.add(new ThrustSample(EngMotorRecord.number(tokens[0],"time_s"),EngMotorRecord.number(tokens[1],"thrust_N")));
                } else throw new IllegalArgumentException("expected seven header fields or two sample fields");
                separated=false;
            } catch(IllegalArgumentException e) {
                throw new IllegalArgumentException(fileName+": line "+(i+1)+": "+e.getMessage(),e);
            }
        }
        if(header!=null) {
            try { records.add(new EngMotorRecord(header,samples,headerLine)); }
            catch(IllegalArgumentException e) { throw new IllegalArgumentException(fileName+": header line "+headerLine+": "+e.getMessage(),e); }
        }
        if(records.isEmpty()) throw new IllegalArgumentException(fileName+": empty motor collection");
        return new EngSourceFile(fileName,revision,sourceReference,snapshot,records,comments);
    }
}
