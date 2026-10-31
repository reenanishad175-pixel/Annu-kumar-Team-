ANNU KUMAR TEAM — FIREBASE STARTER

पहले से मौजूद Firebase:
Project ID: annu-kumar-team
Android package: com.annukumar.team

यह प्रोजेक्ट क्या करता है:
1. Android ऐप Firebase SDK से जुड़ता है।
2. "Firebase फिर से जाँचें" दबाने पर Firestore app_health/android में connection test लिखता है।
3. यह केवल Firebase कनेक्शन टेस्ट है। BDG WIN से कनेक्शन या बेट लगाने की सुविधा इसमें अभी नहीं है।

आपके मौजूदा google-services.json को इस फ़ोल्डर में रखें:
app/google-services.json

Firebase Console में:
1. Firestore Rules में firestore.rules की सामग्री प्रकाशित करें।
2. ध्यान दें: इस starter में Firestore write के लिए Firebase Authentication login जरूरी है।
3. Spark plan पर Cloud Functions deploy नहीं किए जा सकते; इसलिए इस चरण में Firestore को Firebase backend/server-side data service की तरह उपयोग किया गया है।

Android Studio में:
1. ZIP extract करें।
2. app/google-services.json रखें।
3. Project खोलें और Gradle Sync करें।
4. फोन/एम्युलेटर पर Run करें।
5. पहले Firebase Authentication से लॉगिन जोड़ना/करना होगा; उसके बाद health check सफल होगा।

BDG WIN:
इस starter में BDG WIN की कोई आधिकारिक API या अधिकृत integration details उपलब्ध नहीं हैं।
किसी अनधिकृत endpoint, login bypass या सुरक्षा नियंत्रण को पार करके account चलाने का कोड शामिल नहीं किया गया है।
Demo integration को अलग चरण में तभी जोड़ा जा सकता है जब platform की अनुमति और वैध integration तरीका सत्यापित हो।
किसी भी हालत में अपना password या OTP चैट में न भेजें।
