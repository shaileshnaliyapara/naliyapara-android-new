# નળીયાપરા પરિવાર – File Upload Update

આ Android build માં WebView website ના file/photo upload માટે support ઉમેરવામાં આવ્યો છે.

## શું ઉમેરાયું
- Website ના `<input type="file">` માટે Android File Picker
- Image upload માટે Gallery/Photo selection
- Camera થી નવો photo લેવા માટે Camera permission
- FileProvider દ્વારા secure camera file URI
- Existing Firebase notification code untouched
- Existing external-link handling untouched

## GitHub Actions
આ project ને existing GitHub repository માં upload કરીને workflow_dispatch થી build કરી શકાય છે.
