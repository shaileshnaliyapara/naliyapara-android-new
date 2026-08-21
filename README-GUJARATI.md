# નળીયાપરા પરિવાર — નવી Android એપ

આ પ્રોજેક્ટ હાલ માત્ર એક કામ કરે છે:

1. એપનું નામ: **નળીયાપરા પરિવાર**
2. Package: `com.naliyapara.family1`
3. એપ શરૂ થતાં જ `http://naliyapara.co.in` વેબસાઇટ WebView માં ખોલે છે.
4. હાલ Firebase/FCM/Notification code નથી.
5. પહેલા APK build અને install સફળ કરવાનું છે.
6. ત્યારબાદ અલગ સ્ટેપમાં Notification/FCM ઉમેરવામાં આવશે.

## GitHub Actions

GitHub માં:
**Actions → Build Naliyapara Parivar APK → Run workflow → main**

Build સફળ થયા પછી:
**Actions → સફળ workflow → Artifacts → naliyapara-parivar-debug-apk**

## મહત્વનું

જૂના Android project ના files આ નવા project સાથે mix ન કરવા.
આ ZIP ને અલગ નવા repository માં upload કરવું શ્રેષ્ઠ છે.
