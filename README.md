# @cabletv102/capacitor-upload-care-plugin

Utilizes UploadCare's mobile SDK with Ionic/Capacitor/React

## Install

```bash
npm install @cabletv102/capacitor-upload-care-plugin
npx cap sync
```

## API

<docgen-index>

* [`configure(...)`](#configure)
* [`openUploader(...)`](#openuploader)
* [`uploadDataUri(...)`](#uploaddatauri)
* [Interfaces](#interfaces)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### configure(...)

```typescript
configure(options: UploadCareConfig) => Promise<void>
```

Configure the Uploadcare SDK.
Call this early in your app (e.g., app initialization).

| Param         | Type                                                          |
| ------------- | ------------------------------------------------------------- |
| **`options`** | <code><a href="#uploadcareconfig">UploadCareConfig</a></code> |

--------------------


### openUploader(...)

```typescript
openUploader(options?: UploadCareUploadOptions | undefined) => Promise<UploadCareUploadResult>
```

Open the native Uploadcare picker/uploader.
You will call this from Ionic components (IonButton, IonItem, etc.).

| Param         | Type                                                                        |
| ------------- | --------------------------------------------------------------------------- |
| **`options`** | <code><a href="#uploadcareuploadoptions">UploadCareUploadOptions</a></code> |

**Returns:** <code>Promise&lt;<a href="#uploadcareuploadresult">UploadCareUploadResult</a>&gt;</code>

--------------------


### uploadDataUri(...)

```typescript
uploadDataUri(options: UploadCareDataUriOptions) => Promise<UploadCareUploadResult>
```

Upload a base64 data URI directly (no native picker).
Expects a full data URI like: data:image/jpeg;base64,/9j/4AAQSk...

| Param         | Type                                                                          |
| ------------- | ----------------------------------------------------------------------------- |
| **`options`** | <code><a href="#uploadcaredataurioptions">UploadCareDataUriOptions</a></code> |

**Returns:** <code>Promise&lt;<a href="#uploadcareuploadresult">UploadCareUploadResult</a>&gt;</code>

--------------------


### Interfaces


#### UploadCareConfig

Configuration options for Uploadcare SDK.
These map to the public/private (secret) keys you will pass from the host app.

| Prop            | Type                 | Description                                                                                                                                                           |
| --------------- | -------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **`publicKey`** | <code>string</code>  | Your Uploadcare public key (required for client-side uploading). Example: 'demopublickey'                                                                             |
| **`secretKey`** | <code>string</code>  | Optional: Secret key if your mobile SDK usage requires it. Usually used for signed uploads / secure operations. Handle with care and never expose this in web builds. |
| **`cdnBase`**   | <code>string</code>  | Optional: custom CDN base or API base if your Uploadcare project uses them.                                                                                           |
| **`apiBase`**   | <code>string</code>  |                                                                                                                                                                       |
| **`debug`**     | <code>boolean</code> | Whether to enable console logging on native side for debugging.                                                                                                       |


#### UploadCareUploadResult

Result from an Uploadcare upload session.

| Prop               | Type                          | Description                                                                                           |
| ------------------ | ----------------------------- | ----------------------------------------------------------------------------------------------------- |
| **`success`**      | <code>boolean</code>          | Whether the upload interaction completed successfully.                                                |
| **`cancelled`**    | <code>boolean</code>          | If the user cancelled the picker.                                                                     |
| **`errorMessage`** | <code>string</code>           | Error message, if any.                                                                                |
| **`files`**        | <code>UploadCareFile[]</code> | Array of uploaded file descriptors. Even when `multiple` is false, we’ll return an array of length 1. |


#### UploadCareFile

Metadata about a single uploaded file as returned by Uploadcare.

| Prop            | Type                | Description                                                           |
| --------------- | ------------------- | --------------------------------------------------------------------- |
| **`uuid`**      | <code>string</code> | Uploadcare file UUID.                                                 |
| **`cdnUrl`**    | <code>string</code> | Uploadcare CDN URL for this file (what you probably care about most). |
| **`filename`**  | <code>string</code> | Original filename, if available.                                      |
| **`sizeBytes`** | <code>number</code> | File size in bytes.                                                   |
| **`mimeType`**  | <code>string</code> | MIME type, e.g. 'image/jpeg'.                                         |
| **`width`**     | <code>number</code> | Width & height if it’s an image (when available).                     |
| **`height`**    | <code>number</code> |                                                                       |


#### UploadCareUploadOptions

Options for an individual upload interaction.

| Prop                   | Type                  | Description                                                                                                                  |
| ---------------------- | --------------------- | ---------------------------------------------------------------------------------------------------------------------------- |
| **`multiple`**         | <code>boolean</code>  | Allow selecting multiple files.                                                                                              |
| **`allowedMimeTypes`** | <code>string[]</code> | Restrict to images, videos, etc. If omitted, use Uploadcare defaults. We’ll map this to native Uploadcare type restrictions. |
| **`maxFiles`**         | <code>number</code>   | Max number of files when `multiple` is true.                                                                                 |
| **`enableCrop`**       | <code>boolean</code>  | Whether to enable cropping UI (if supported by the SDK).                                                                     |
| **`cropRatio`**        | <code>string</code>   | Preferred crop aspect ratio, like '3:2', '1:1', etc. We’ll parse and map this on native side if Uploadcare supports it.      |
| **`maxFileSizeBytes`** | <code>number</code>   | Max file size in bytes (we can enforce in native before uploading).                                                          |


#### UploadCareDataUriOptions

Options for uploading a pre-existing base64 data URI.
The `dataUri` must be a full data URI string:
  data:&lt;mime&gt;;base64,&lt;payload&gt;

| Prop           | Type                |
| -------------- | ------------------- |
| **`dataUri`**  | <code>string</code> |
| **`fileName`** | <code>string</code> |

</docgen-api>
# capacitor-upload-care-plugin
