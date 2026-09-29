package org.aliveclean;

import android.content.Context;
import java.io.*;
import java.nio.channels.FileLock;
import java.security.MessageDigest;
import org.json.*;
import org.tukaani.xz.XZInputStream;

/** Lossless, shared storage; reconstructed APKs retain their original signatures. */
final class XiaomiBuiltinPacks {
    private static final int CHUNK_LIMIT=4*1024*1024;
    private XiaomiBuiltinPacks(){}

    // Callers prepare off the UI thread. The file lock also covers other wallpaper
    // and preview processes; a partial extraction never replaces a usable APK.
    static synchronized File prepare(Context context,XiaomiPacks.Pack pack)throws Exception {
        File directory=XiaomiPacks.directory(context),target=new File(directory,pack.digest+".apk");
        try(FileOutputStream lockStream=new FileOutputStream(new File(directory,pack.digest+".lock"),true);
            FileLock lock=lockStream.getChannel().lock()){
            if(target.isFile())try{XiaomiPacks.verify(target,pack);return target;}catch(IOException corrupt){}
            JSONObject manifest=new JSONObject(AssetGl.text(context.getAssets(),"xiaomi/packs.json"));
            if(manifest.getInt("format")!=1||manifest.getInt("max_chunk_bytes")!=CHUNK_LIMIT)throw new IOException("Unsupported built-in wallpaper format");
            JSONObject original=manifest.getJSONObject("packs").getJSONObject(pack.id);
            if(!pack.digest.equals(original.getString("sha256"))||pack.bytes!=original.getLong("bytes"))throw new IOException("Built-in wallpaper catalog mismatch");
            JSONArray chunks=original.getJSONArray("chunks");
            File part=File.createTempFile("builtin-",".part",directory);
            try{
                MessageDigest whole=MessageDigest.getInstance("SHA-256");long total=0;
                try(FileOutputStream out=new FileOutputStream(part)){
                    if(!part.setReadOnly())throw new IOException("Cannot protect built-in wallpaper code");
                    byte[] buffer=new byte[65536];
                    for(int index=0;index<chunks.length();index++){
                        JSONArray chunk=chunks.getJSONArray(index);String digest=chunk.getString(0);int expected=chunk.getInt(1);
                        if(!digest.matches("[a-f0-9]{64}")||expected<=0||expected>CHUNK_LIMIT||total+expected>pack.bytes)throw new IOException("Invalid wallpaper chunk");
                        MessageDigest check=MessageDigest.getInstance("SHA-256");int count=0;
                        try(InputStream asset=context.getAssets().open("xiaomi/packs/"+digest+".xz");
                            InputStream input=new XZInputStream(asset,32*1024)){
                            for(int read;(read=input.read(buffer))!=-1;){
                                count+=read;if(count>expected)throw new IOException("Wallpaper chunk exceeds expected size");
                                check.update(buffer,0,read);whole.update(buffer,0,read);out.write(buffer,0,read);
                            }
                        }
                        if(count!=expected||!digest.equals(hex(check.digest())))throw new IOException("Wallpaper chunk checksum mismatch");
                        total+=count;
                    }
                    if(total!=pack.bytes||!pack.digest.equals(hex(whole.digest())))throw new IOException("Built-in wallpaper checksum mismatch");
                    out.getFD().sync();
                }
                if(!part.renameTo(target))throw new IOException("Cannot save built-in wallpaper");
                return target;
            }finally{if(part.exists())part.delete();}
        }
    }
    private static String hex(byte[] value){char[] chars="0123456789abcdef".toCharArray(),out=new char[value.length*2];for(int i=0;i<value.length;i++){out[i*2]=chars[(value[i]&255)>>>4];out[i*2+1]=chars[value[i]&15];}return new String(out);}
}
