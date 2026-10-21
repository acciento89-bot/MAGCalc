package de.kamilunavo.approvedcalcqa;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.StaleObjectException;
import androidx.test.uiautomator.Until;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.*;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;
import static org.junit.Assert.*;

/** External instrumentation host: only actual installed native UI writes target-app test data. */
@RunWith(AndroidJUnit4.class)
public class ApprovedCalcNativeTest {
    private static final String MAG = "de.kamilunavo.magcalc";
    private final UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    private final android.content.Context host = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final File evidence = new File(host.getFilesDir(), "approvedcalc-native-qa");
    private String method = "unknown";

    @Rule public final TestWatcher result = new TestWatcher() {
        @Override protected void starting(Description description) { method = description.getMethodName(); evidence.mkdirs(); event("START"); }
        @Override protected void succeeded(Description description) { event("PASS"); }
        @Override protected void failed(Throwable error, Description description) { capture(method + "-FAIL"); event("FAIL: " + error); }
    };
    @Before public void rootOwnedApi35EmulatorOnly() throws Exception {
        assertEquals("This harness is for the root-owned API35 disposable emulator", 35, Build.VERSION.SDK_INT);
        String qemu = device.executeShellCommand("getprop ro.kernel.qemu").trim();
        assertEquals("Do not run this synthetic-data harness on a personal physical device", "1", qemu);
        requireResponsiveSystemUI();
        device.setOrientationNatural(); device.pressHome();
    }
    @After public void restoreRotation() throws Exception { device.setOrientationNatural(); device.unfreezeRotation(); }

    @Test public void magProjectAlternativeBaselineRotationColdReloadAndRealShare() throws Exception {
        String name = "QA MAG " + System.currentTimeMillis();
        launch(MAG, true); click("Gespeicherte Projekte", "Saved projects"); click("Neues Projekt", "New project");
        fill("Name", "Name", name); fill("Anlagennotizen", "Installation notes", "QA manufacturer limits must be verified");
        rotateDraft(name, "mag-project-draft-landscape"); click("Speichern", "Save");
        click("Als Alternative duplizieren", "Duplicate as alternative");
        // Locate the copied scenario itself, then its following action; never edit the baseline by index.
        clickFollowing(new String[]{"Referenz · Kopie", "Baseline · Copy"}, "Szenario bearbeiten", "Edit scenario");
        fill("Name", "Name", "QA Alternative"); fill("Anlagenvolumen", "System volume", "600");
        rotateDraft("QA Alternative", "mag-scenario-draft-landscape");
        assertFieldValue("Anlagenvolumen", "System volume", "600");
        fill("Anlagenvolumen", "System volume", "");
        assertFalse("blank visible system volume must disable Save", control(find("Speichern", "Save")).isEnabled());
        fill("Anlagenvolumen", "System volume", "600"); click("Speichern", "Save");
        assertVisibleContains("Differenz zur Referenz", "difference from baseline"); capture("mag-baseline-comparison");
        JSONObject saved = project(MAG, "vessel-projects.json", name);
        JSONArray scenarios = saved.getJSONArray("scenarios"); assertEquals(2, scenarios.length());
        JSONObject alternative = named(scenarios, "QA Alternative");
        assertEquals(600.0, alternative.getDouble("systemVolume"), 0.0);
        JSONObject baseline = byId(scenarios, saved.getString("baselineID")); assertEquals(300.0, baseline.getDouble("systemVolume"), 0.0);
        assertNotEquals(baseline.getString("id"), alternative.getString("id"));
        saveSelected("mag-synthetic-saved.json", saved);
        launch(MAG, true); click("Gespeicherte Projekte", "Saved projects");
        // Locate the synthetic project card, then its own Open project action.
        clickProjectCard(name, "Projekt öffnen", "Open project"); assertVisibleContains("QA Alternative");
        assertEquals(saved.toString(), project(MAG, "vessel-projects.json", name).toString());
        capture("mag-cold-reloaded"); share(MAG, "Berechnungsprotokoll teilen", "Share calculation record", "mag-actual-share", "QA Alternative", name);
    }

    private void launch(String pkg, boolean cold) throws Exception {
        requireResponsiveSystemUI();
        ApplicationInfo info = host.getPackageManager().getApplicationInfo(pkg, 0);
        assertTrue("Only debug candidates on disposable emulator", (info.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0);
        android.content.pm.PackageInfo installed = host.getPackageManager().getPackageInfo(pkg, 0);
        event("ACTUAL_APP " + pkg + " " + installed.versionName + "(" + installed.getLongVersionCode() + ")");
        if (cold) device.executeShellCommand("am force-stop " + pkg);
        Intent intent = host.getPackageManager().getLaunchIntentForPackage(pkg); assertNotNull("Missing preinstalled actual app " + pkg, intent);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK); host.startActivity(intent);
        assertTrue("App did not foreground: " + pkg, device.wait(Until.hasObject(By.pkg(pkg).depth(0)), 15000)); device.waitForIdle(1000);
    }
    private Pattern pattern(String... labels) {
        StringBuilder p = new StringBuilder("(?:"); for (int i = 0; i < labels.length; i++) { if (i > 0) p.append('|'); p.append(Pattern.quote(labels[i])); } return Pattern.compile(p.append(')').toString());
    }
    private List<UiObject2> visible(String... labels) {
        List<UiObject2> nodes = device.findObjects(By.text(pattern(labels)));
        if (nodes.isEmpty()) nodes = device.findObjects(By.desc(pattern(labels)));
        if (nodes.isEmpty()) nodes = device.findObjects(By.text(Pattern.compile(".*"+pattern(labels).pattern()+".*",Pattern.DOTALL)));
        if (nodes.isEmpty()) nodes = device.findObjects(By.desc(Pattern.compile(".*"+pattern(labels).pattern()+".*",Pattern.DOTALL)));
        return nodes;
    }
    private UiObject2 find(String... labels) {
        requireResponsiveSystemUI();
        hideKeyboard();
        for (int sweep = 0; sweep < 2; sweep++) {
            for (int i = 0; i < 12; i++) { requireResponsiveSystemUI(); List<UiObject2> nodes = visible(labels); if (!nodes.isEmpty()) return nodes.get(0); scroll(false); }
            top();
        }
        capture(method + "-missing-label"); throw new AssertionError("Missing native label " + java.util.Arrays.toString(labels));
    }
    private UiObject2 control(UiObject2 node) {
        UiObject2 current = node;
        for (int i = 0; i < 5 && current != null; i++, current = current.getParent()) {
            if ("android.widget.Button".equals(current.getClassName()) || current.isClickable()) return current;
        }
        return node;
    }
    private void click(String... labels) { control(find(labels)).click(); device.waitForIdle(1000); }
    private List<UiObject2> exactVisible(String... labels) {
        List<UiObject2> nodes = device.findObjects(By.text(pattern(labels)));
        if (nodes.isEmpty()) nodes = device.findObjects(By.desc(pattern(labels)));
        return nodes;
    }
    private UiObject2 findExact(String... labels) {
        requireResponsiveSystemUI();
        hideKeyboard(); top();
        for (int attempt=0; attempt<16; attempt++) {
            requireResponsiveSystemUI();
            List<UiObject2> nodes=exactVisible(labels);
            if(!nodes.isEmpty()) return nodes.get(0);
            scroll(false);
        }
        capture(method+"-missing-exact-label");
        throw new AssertionError("Missing exact native card label "+java.util.Arrays.toString(labels));
    }
    private void clickFollowing(String[] labels, String... actions) {
        // Report previews repeat scenario names inside a large paragraph. They
        // are not a card title and can never anchor its Edit/Open action.
        findExact(labels);
        for (int attempt=0; attempt<12; attempt++) {
            List<UiObject2> visibleLabels=exactVisible(labels);
            int minimumY=visibleLabels.isEmpty()?0:visibleLabels.get(0).getVisibleBounds().bottom;
            UiObject2 candidate=null;
            for(UiObject2 action:exactVisible(actions)) {
                if(action.getVisibleBounds().top>=minimumY && (candidate==null || action.getVisibleBounds().top<candidate.getVisibleBounds().top)) candidate=action;
            }
            if(candidate!=null) { control(candidate).click(); device.waitForIdle(1000); return; }
            scroll(false);
        }
        throw new AssertionError("No following native action after "+java.util.Arrays.toString(labels));
    }
    private void requireResponsiveSystemUI() {
        Pattern systemAnr=Pattern.compile("(?is).*(?:System UI isn't responding|System-UI reagiert nicht|System UI reagiert nicht).*");
        if(device.hasObject(By.pkg("android").text(systemAnr))) {
            capture(method+"-SYSTEM-UI-ANR");
            event("ENVIRONMENT_BLOCKED System UI ANR; no Wait/Close interaction");
            throw new AssertionError("ENVIRONMENT_BLOCKED: Android System UI ANR obstructs native input; evidence preserved, no dialog dismissed");
        }
    }
    private <T> T freshEditor(String operation, java.util.function.Supplier<T> readOrSet) {
        StaleObjectException stale=null;
        for(int attempt=0;attempt<4;attempt++) {
            requireResponsiveSystemUI();
            try { return readOrSet.get(); }
            catch(StaleObjectException changedTree) {
                stale=changedTree;
                event("REQUERY stale native editor "+operation+" attempt="+(attempt+1));
                device.waitForIdle(500); SystemClock.sleep(150);
            }
        }
        capture(method+"-stale-editor");
        throw new AssertionError("Native editor remained stale after four fresh label queries: "+operation,stale);
    }
    private UiObject2 editFor(String de, String en) {
        UiObject2 label = find(de, en), current = label;
        for (int i = 0; i < 5 && current != null; i++, current = current.getParent()) {
            if ("android.widget.EditText".equals(current.getClassName())) return current;
            List<UiObject2> edits = current.findObjects(By.clazz("android.widget.EditText"));
            if (edits.size() == 1) return edits.get(0);
            // Classic Brenner fields expose a standalone label followed by a
            // sibling EditText; the section contains several such pairs.
            android.graphics.Rect labelBounds=label.getVisibleBounds();
            UiObject2 next=null;
            for(UiObject2 editor:edits) {
                android.graphics.Rect bounds=editor.getVisibleBounds();
                if(!bounds.isEmpty() && bounds.top>=labelBounds.bottom &&
                        bounds.left<=labelBounds.centerX() && bounds.right>=labelBounds.centerX() &&
                        (next==null || bounds.top<next.getVisibleBounds().top)) next=editor;
            }
            if(next!=null) return next;
        }
        label.click(); device.waitForIdle(1000);
        UiObject2 focused = device.findObject(By.pkg(label.getApplicationPackage()).clazz("android.widget.EditText").focused(true));
        android.graphics.Rect labelBounds=label.getVisibleBounds();
        if (focused == null || !focused.getVisibleBounds().contains(labelBounds.centerX(),labelBounds.centerY()))
            throw new AssertionError("No label-matched focused text editor for " + de + "/" + en);
        return focused;
    }
    private void fill(String de, String en, String text) { freshEditor(de,()->{ editFor(de,en).setText(text); return true; }); device.waitForIdle(1000); hideKeyboard(); }
    private void assertFieldValue(String de, String en, String text) { assertEquals("Visible draft field " + de, text, freshEditor(de,()->editFor(de,en).getText())); }
    private void rotateDraft(String expected, String shot) throws Exception {
        hideKeyboard(); device.setOrientationLeft(); device.waitForIdle(1000); assertVisibleContains(expected); capture(shot);
        device.setOrientationNatural(); device.waitForIdle(1000); assertVisibleContains(expected);
    }
    private void assertVisibleContains(String... fragments) {
        hideKeyboard();
        for (int sweep=0; sweep<2; sweep++) {
            for(int i=0;i<14;i++) { for(String part:fragments) if(device.hasObject(By.textContains(part))) return; scroll(false); } top();
        }
        capture(method + "-missing-value"); throw new AssertionError("Native UI does not contain " + java.util.Arrays.toString(fragments));
    }
    private void clickProjectCard(String name, String deAction, String enAction) { clickFollowing(new String[]{name},deAction,enAction); }
    private void share(String pkg, String de, String en, String shot, String... expectedFacts) throws Exception {
        File incoming = new File(evidence,"last-actual-share.txt");
        if(incoming.exists()) assertTrue(incoming.delete());
        click(de, en); long end = SystemClock.uptimeMillis()+10000;
        while(SystemClock.uptimeMillis()<end && pkg.equals(device.getCurrentPackageName())) SystemClock.sleep(150);
        assertNotEquals("Actual share sheet must leave app window", pkg, device.getCurrentPackageName()); capture(shot);
        // Select only the local QA receiver. Never choose email, messaging, cloud or external contacts.
        click("QA local capture");
        end=SystemClock.uptimeMillis()+10000;
        while(SystemClock.uptimeMillis()<end && !incoming.isFile()) SystemClock.sleep(100);
        assertTrue("Actual ACTION_SEND payload did not reach the local QA receiver",incoming.isFile());
        String payload=new String(Files.readAllBytes(incoming.toPath()),StandardCharsets.UTF_8);
        for(String fact:expectedFacts) assertTrue("Actual shared payload omitted "+fact,payload.contains(fact));
        Files.write(new File(evidence,shot+"-payload.txt").toPath(),payload.getBytes(StandardCharsets.UTF_8));
        capture(shot+"-locally-captured"); device.pressBack();
    }
    private void scroll(boolean up) {
        int w=device.getDisplayWidth(),h=device.getDisplayHeight();
        device.swipe(w/2, up?h/3:h*4/5, w/2, up?h*4/5:h/3, 30); device.waitForIdle(1000);
    }
    private void top() { for(int i=0;i<10;i++) scroll(true); }
    private void hideKeyboard() {
        try { String state=device.executeShellCommand("dumpsys input_method"); if(Pattern.compile("(?:mInputShown|mIsInputViewShown|isInputViewShown)\\s*=\\s*true").matcher(state).find()) { device.pressBack(); device.waitForIdle(1000); } }
        catch(Exception e) { throw new AssertionError("Cannot check IME state",e); }
    }
    private JSONObject project(String pkg, String relative, String name) throws Exception {
        String raw=device.executeShellCommand("run-as "+pkg+" cat files/"+relative); JSONObject document=new JSONObject(raw); return named(document.getJSONArray("projects"),name);
    }
    private JSONObject named(JSONArray values,String name) throws Exception { for(int i=0;i<values.length();i++) { JSONObject v=values.getJSONObject(i); if(name.equals(v.getString("name"))) return v; } throw new AssertionError("No saved synthetic project "+name); }
    private JSONObject byId(JSONArray values,String id) throws Exception { for(int i=0;i<values.length();i++) { JSONObject v=values.getJSONObject(i); if(id.equals(v.getString("id"))) return v; } throw new AssertionError("Missing baseline ID "+id); }
    private JSONObject noted(JSONArray values,String note) throws Exception { for(int i=0;i<values.length();i++) { JSONObject v=values.getJSONObject(i); if(note.equals(v.getString("note"))) return v; } throw new AssertionError("Missing visit "+note); }
    private void saveSelected(String name, JSONObject selected) throws Exception { Files.write(new File(evidence,name).toPath(),selected.toString(2).getBytes(StandardCharsets.UTF_8)); }
    private void capture(String name) { try { evidence.mkdirs(); assertTrue(device.takeScreenshot(new File(evidence,name+".png"))); device.dumpWindowHierarchy(new File(evidence,name+".xml")); } catch(Exception e) { throw new AssertionError("Cannot capture actual native evidence",e); } }
    private void event(String state) { try { evidence.mkdirs(); try(java.io.FileOutputStream stream=new java.io.FileOutputStream(new File(evidence,"events.jsonl"),true)) { stream.write((new JSONObject().put("method",method).put("state",state).put("at",System.currentTimeMillis()).toString()+"\n").getBytes(StandardCharsets.UTF_8)); } } catch(Exception e) { throw new AssertionError(e); } }
}
