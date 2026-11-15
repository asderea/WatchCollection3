package com.watchcollection.app;

import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.lang.reflect.Constructor;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** 실제 Google 계정과 네트워크를 사용하지 않는 상태 전이 검증입니다. */
public final class CheckDriveClient {
    static int checks;
    static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }

    static final class Prefs implements SharedPreferences {
        final Map<String,Object> values = new HashMap<>();
        public String getString(String k,String f){return values.containsKey(k)?(String)values.get(k):f;}
        public long getLong(String k,long f){return values.containsKey(k)?(Long)values.get(k):f;}
        public boolean getBoolean(String k,boolean f){return values.containsKey(k)?(Boolean)values.get(k):f;}
        public Editor edit(){return new Edit();}
        final class Edit implements Editor {
            final Map<String,Object> changed=new HashMap<>(); final Set<String> removed=new HashSet<>();
            public Editor putString(String k,String v){changed.put(k,v);return this;}
            public Editor putLong(String k,long v){changed.put(k,v);return this;}
            public Editor putBoolean(String k,boolean v){changed.put(k,v);return this;}
            public Editor remove(String k){removed.add(k);return this;}
            public boolean commit(){for(String k:removed)values.remove(k);values.putAll(changed);return true;}
            public void apply(){commit();}
        }
    }

    static final class Reply {
        final int code; final String body; final String location;
        Reply(int c,String b,String l){code=c;body=b;location=l;}
    }
    static final class Pending {
        final String id; final JSONObject metadata;
        Pending(String i,JSONObject m){id=i;metadata=m;}
    }
    static final class FakeDrive implements GoogleDriveClient.ConnectionFactory {
        final Map<String,JSONObject> files=new LinkedHashMap<>();
        final Map<String,byte[]> contents=new HashMap<>();
        final Map<String,String> owners=new HashMap<>();
        final Map<String,Pending> sessions=new HashMap<>();
        final List<String> savedKinds=new ArrayList<>();
        String account="accountA"; int counter=0, imageWrites=0, metadataReads=0;
        boolean failDocument=false, failWrites=false, deleteDocumentOnce=false, rejectAbout=false;
        int imageFailure=0;
        public HttpURLConnection open(URL url){return new FakeConnection(url,this);}
        Reply ok(JSONObject value){return new Reply(200,value.toString(),null);}
        Reply error(int status){return new Reply(status,"{\"error\":{\"message\":\"TOKEN-TOP-SECRET\",\"errors\":[{\"reason\":\"TOKEN-TOP-SECRET\"}]}}",null);}
        Reply handle(FakeConnection request)throws Exception{
            String path=request.url.getPath();Map<String,String> q=query(request.url);
            check(!path.contains("permissions"),"공개 공유 API 호출 없음");
            check("Bearer TOKEN-TOP-SECRET".equals(request.headers.get("Authorization")),"헤더로만 토큰 전달");
            if(path.equals("/drive/v3/about"))return rejectAbout?error(401):ok(new JSONObject().put("user",new JSONObject().put("permissionId",account).put("emailAddress",account+"@example.test")));
            if(path.equals("/drive/v3/files")&&request.method.equals("GET")){
                String raw=q.get("q"), kind=part(raw,"wc_kind"), key=part(raw,"wc_key");
                String parent=null;int parentEnd=raw.indexOf("' in parents");if(parentEnd>=0){int start=raw.lastIndexOf("'",parentEnd-1);parent=raw.substring(start+1,parentEnd);}
                JSONArray found=new JSONArray();for(Map.Entry<String,JSONObject> item:files.entrySet()){
                    JSONObject f=item.getValue(),p=f.getJSONObject("appProperties");
                    if(!account.equals(owners.get(item.getKey()))||f.optBoolean("trashed"))continue;
                    if(kind.equals(p.optString("wc_kind"))&&key.equals(p.optString("wc_key"))&&(parent==null||parent.equals(f.optJSONArray("parents").optString(0))))found.put(copy(f));
                }
                return ok(new JSONObject().put("files",found));
            }
            if(path.startsWith("/drive/v3/files/")&&request.method.equals("GET")){
                metadataReads++;String id=path.substring("/drive/v3/files/".length());JSONObject f=files.get(id);
                return f==null||!account.equals(owners.get(id))?error(404):ok(copy(f));
            }
            if(path.equals("/drive/v3/files")&&request.method.equals("POST")){
                if(failWrites)return error(503);JSONObject f=new JSONObject(request.text());String id="file"+(++counter);f.put("id",id).put("ownedByMe",true);files.put(id,f);owners.put(id,account);return ok(copy(f));
            }
            if(path.startsWith("/upload/drive/v3/files")){
                if(request.method.equals("PUT")){
                    Pending p=sessions.get(q.get("upload_id"));if(p==null)return error(404);
                    String kind=p.metadata.getJSONObject("appProperties").optString("wc_kind");
                    if(failWrites||(kind.equals("document")&&failDocument))return error(503);
                    if(kind.equals("image")&&imageFailure!=0)return error(imageFailure);
                    String id=p.id==null?"file"+(++counter):p.id;JSONObject old=files.get(id);
                    JSONObject f=copy(p.metadata);if(old!=null&&!f.has("parents"))f.put("parents",old.getJSONArray("parents"));
                    f.put("id",id).put("ownedByMe",true).put("md5Checksum",hash("MD5",request.data.toByteArray()));
                    files.put(id,f);owners.put(id,account);contents.put(id,request.data.toByteArray());savedKinds.add(kind);if(kind.equals("image"))imageWrites++;
                    return ok(copy(f));
                }
                if(failWrites)return error(503);String suffix=path.substring("/upload/drive/v3/files".length());String id=suffix.isEmpty()?null:suffix.substring(1);
                JSONObject body=new JSONObject(request.text());String kind=body.getJSONObject("appProperties").optString("wc_kind");
                if(id!=null){check("PATCH".equals(request.headers.get("X-HTTP-Method-Override")),"기존 ID PATCH 갱신");if(kind.equals("document")&&deleteDocumentOnce){files.remove(id);deleteDocumentOnce=false;return error(404);}if(!files.containsKey(id))return error(404);}
                String session="session"+(++counter);sessions.put(session,new Pending(id,body));return new Reply(200,"","https://www.googleapis.com/upload/drive/v3/files?upload_id="+session);
            }
            throw new AssertionError("Unexpected fake endpoint: "+path);
        }
        static JSONObject copy(JSONObject value)throws Exception{return new JSONObject(value.toString());}
        static String part(String q,String key){String prefix="key='"+key+"' and value='";int start=q.indexOf(prefix)+prefix.length();return q.substring(start,q.indexOf("'",start));}
        static Map<String,String> query(URL url)throws Exception{Map<String,String> result=new HashMap<>();if(url.getQuery()!=null)for(String p:url.getQuery().split("&")){String[]v=p.split("=",2);result.put(URLDecoder.decode(v[0],"UTF-8"),v.length==1?"":URLDecoder.decode(v[1],"UTF-8"));}return result;}
    }
    static final class FakeConnection extends HttpURLConnection {
        final URL url;final FakeDrive backend;final Map<String,String>headers=new HashMap<>();final ByteArrayOutputStream data=new ByteArrayOutputStream();String method;Reply reply;
        FakeConnection(URL u,FakeDrive b){super(u);url=u;backend=b;}
        public void setRequestMethod(String m){method=m;}
        public void setRequestProperty(String k,String v){headers.put(k,v);}
        public OutputStream getOutputStream(){return data;}
        String text(){return new String(data.toByteArray(),StandardCharsets.UTF_8);}
        void finish()throws IOException{if(reply==null)try{reply=backend.handle(this);}catch(Exception e){throw new IOException(e);}}
        public int getResponseCode()throws IOException{finish();return reply.code;}
        public InputStream getInputStream()throws IOException{finish();return new ByteArrayInputStream(reply.body.getBytes(StandardCharsets.UTF_8));}
        public InputStream getErrorStream(){return new ByteArrayInputStream(reply.body.getBytes(StandardCharsets.UTF_8));}
        public String getHeaderField(String k){return k.equals("Location")?reply.location:null;}
        public void connect(){}public boolean usingProxy(){return false;}public void disconnect(){}
    }

    static String hash(String algorithm,byte[] data)throws Exception{StringBuilder s=new StringBuilder();for(byte b:MessageDigest.getInstance(algorithm).digest(data))s.append(String.format("%02x",b&255));return s.toString();}
    static CollectionSnapshotExporter.ImageAsset asset(Path file)throws Exception{
        Constructor<CollectionSnapshotExporter.ImageAsset> c=CollectionSnapshotExporter.ImageAsset.class.getDeclaredConstructor(String.class,String.class,String.class,String.class,String.class,File.class);c.setAccessible(true);
        return c.newInstance(hash("SHA-256",Files.readAllBytes(file)),file.toString(),"image/png",file.getFileName().toString(),"시험 사진",file.toFile());
    }
    static CollectionSnapshotExporter.Export snapshot(List<CollectionSnapshotExporter.ImageAsset> images)throws Exception{
        JSONObject tables=new JSONObject();JSONArray watches=new JSONArray();
        if(!images.isEmpty())watches.put(new JSONObject().put("id",1).put("model_name","Fixture Watch").put("original_image_url",images.get(0).sourcePath));
        tables.put("watches",watches);
        JSONObject json=new JSONObject().put("tables",tables).put("exported_at","2026-11-14T12:00:00Z");
        Constructor<CollectionSnapshotExporter.Export> c=CollectionSnapshotExporter.Export.class.getDeclaredConstructor(JSONObject.class,String.class,List.class,List.class);c.setAccessible(true);
        return c.newInstance(json,"snapshot-fixture-hash",images,Collections.emptyList());
    }
    static GoogleDriveClient client(FakeDrive drive){return new GoogleDriveClient("TOKEN-TOP-SECRET",drive);}

    public static void main(String[]args)throws Exception{
        Path file=Files.createTempFile("drive-photo-",".png");Files.write(file,new byte[]{(byte)137,80,78,71,13,10,26,10,1});
        CollectionSnapshotExporter.Export export=snapshot(Collections.singletonList(asset(file)));
        FakeDrive drive=new FakeDrive();Prefs prefs=new Prefs();
        GoogleDriveClient.SyncResult first=client(drive).sync(export,prefs);
        check(first.warnings.isEmpty(),"정상 내보내기 경고 없음");
        check(drive.savedKinds.equals(Arrays.asList("image","snapshot","document")),"사진→JSON→문서 저장 순서");
        check(prefs.getLong("drive_last_success_at",0)==first.uploadedAt,"최종 성공 시각 기록");
        check(!export.json.has("image_files")&&!export.json.has("drive_sync"),"스냅샷 원본 불변");
        check(!prefs.values.toString().contains("TOKEN-TOP-SECRET"),"인증 토큰 미저장");
        String firstDoc=new String(drive.contents.get(first.docId),StandardCharsets.UTF_8);
        check(firstDoc.contains("내보내기 리비전")&&firstDoc.contains("논리 DB JSON"),"문서 리비전/JSON 링크");
        GoogleDriveClient.SyncResult second=client(drive).sync(export,prefs);
        check(second.docId.equals(first.docId)&&second.folderId.equals(first.folderId),"반복 동기화 동일 ID");
        check(drive.imageWrites==1,"동일 사진 바이트 재전송 생략");
        String previousRevision=prefs.getString("drive_last_success_revision","");long previousAt=prefs.getLong("drive_last_success_at",0);
        byte[]previousDoc=drive.contents.get(first.docId);drive.failDocument=true;
        try{client(drive).sync(export,prefs);throw new AssertionError("문서 실패 미검출");}catch(GoogleDriveClient.DriveException expected){check(expected.statusCode==503,"문서 실패 상태 코드");check(!expected.getMessage().contains("TOKEN-TOP-SECRET")&&!expected.reason.contains("TOKEN-TOP-SECRET"),"서버 오류 토큰 제거");}
        check(previousRevision.equals(prefs.getString("drive_last_success_revision",""))&&previousAt==prefs.getLong("drive_last_success_at",0),"중간 실패 성공 상태 보존");
        check(Arrays.equals(previousDoc,drive.contents.get(first.docId)),"최종 업로드 전 실패 시 이전 문서 유지");drive.failDocument=false;
        drive.files.get(first.folderId).put("trashed",true);
        GoogleDriveClient.SyncResult recreated=client(drive).sync(export,prefs);
        check(!recreated.folderId.equals(first.folderId)&&!recreated.docId.equals(first.docId),"삭제된 부모 폴더 복구");
        drive.deleteDocumentOnce=true;GoogleDriveClient.SyncResult raced=client(drive).sync(export,prefs);
        check(!raced.docId.equals(recreated.docId),"조회/쓰기 사이 404 문서 재생성");
        drive.account="accountB";drive.failWrites=true;prefs.values.put("last_warnings","old-account-warning");
        try{client(drive).sync(export,prefs);throw new AssertionError("계정 변경 실패 미검출");}catch(GoogleDriveClient.DriveException expected){}
        check(prefs.getString("drive_account_email","").equals("accountB@example.test"),"변경된 계정 확인");
        check(prefs.getString("drive_doc_url","").isEmpty()&&prefs.getLong("drive_last_success_at",0)==0&&!prefs.values.containsKey("last_warnings"),"다른 계정의 성공 링크/경고 초기화");
        drive.failWrites=false;GoogleDriveClient.SyncResult accountB=client(drive).sync(export,prefs);check(!accountB.folderId.equals(raced.folderId),"계정별 폴더 격리");
        FakeDrive failingImages=new FakeDrive();failingImages.imageFailure=503;Prefs imagePrefs=new Prefs();GoogleDriveClient.SyncResult partial=client(failingImages).sync(export,imagePrefs);
        check(!partial.warnings.isEmpty()&&imagePrefs.getBoolean("drive_needs_retry",false),"일시 사진 실패 경고와 재시도 상태");
        check(new String(failingImages.contents.get(partial.docId),StandardCharsets.UTF_8).contains("Drive 링크를 만들지 못함"),"사진 실패 문서에 명시");
        failingImages.imageFailure=0;client(failingImages).sync(export,imagePrefs);check(!imagePrefs.getBoolean("drive_needs_retry",true),"사진 재시도 성공 후 상태 해제");
        FakeDrive unauthorized=new FakeDrive();unauthorized.imageFailure=401;Prefs unauthorizedPrefs=new Prefs();
        try{client(unauthorized).sync(export,unauthorizedPrefs);throw new AssertionError("401 미검출");}catch(GoogleDriveClient.AuthException expected){check(expected.statusCode==401,"인증 만료 전용 예외");}
        check(!unauthorized.savedKinds.contains("snapshot")&&unauthorizedPrefs.getLong("drive_last_success_at",0)==0,"사진 401이면 전체 중단");
        Files.write(file,new byte[]{9,8,7});FakeDrive changedPhoto=new FakeDrive();Prefs changedPrefs=new Prefs();GoogleDriveClient.SyncResult changed=client(changedPhoto).sync(export,changedPrefs);
        check(changedPhoto.imageWrites==0&&!changed.warnings.isEmpty()&&changedPrefs.getBoolean("drive_needs_retry",false),"사진 경합 해시 불일치 방지");
        Files.delete(file);FakeDrive missingPhoto=new FakeDrive();Prefs missingPrefs=new Prefs();GoogleDriveClient.SyncResult missing=client(missingPhoto).sync(export,missingPrefs);
        check(!missing.warnings.isEmpty()&&!missingPrefs.getBoolean("drive_needs_retry",true),"삭제된 로컬 사진 무한 자동 재시도 방지");
        System.out.println("DRIVE_CHECKS="+checks+" PASS (in-memory HTTP only)");
    }
}
