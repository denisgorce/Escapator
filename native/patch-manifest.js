var fs = require('fs'), f = 'android/app/src/main/AndroidManifest.xml', x = fs.readFileSync(f, 'utf8');
var perms = ['CAMERA', 'RECORD_AUDIO', 'MODIFY_AUDIO_SETTINGS', 'ACCESS_FINE_LOCATION', 'ACCESS_COARSE_LOCATION', 'VIBRATE', 'NFC', 'FLASHLIGHT']
  .map(function(p){ return '    <uses-permission android:name="android.permission.' + p + '" />'; }).join('\n');
var feats = ['camera', 'camera.flash', 'nfc', 'sensor.barometer', 'sensor.proximity', 'sensor.light', 'sensor.compass', 'location.gps', 'microphone']
  .map(function(p){ return '    <uses-feature android:name="android.hardware.' + p + '" android:required="false" />'; }).join('\n');
if (x.indexOf('RECORD_AUDIO') < 0) x = x.replace('</manifest>', perms + '\n' + feats + '\n</manifest>');
if (x.indexOf('screenOrientation') < 0) x = x.replace('android:name=".MainActivity"', 'android:name=".MainActivity"\n            android:screenOrientation="portrait"');
fs.writeFileSync(f, x);
console.log('Manifest patché');
