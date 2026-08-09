# Sample plugin keep rules: the NFC toggle reflects on a hidden system API
# (NfcManager.setNfcEnabled/setNfcDisabled). Nothing is minified in this
# template, but keep the rules here for when you enable shrinking.
-keep class com.nexaflow.sample.nfctoggle.** { *; }
