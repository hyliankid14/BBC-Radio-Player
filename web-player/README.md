# BBC Radio Player - Web Share UI

This directory contains the GitHub Pages web UI for sharing podcasts and episodes from the BBC Radio Player app.

## Setup Instructions

### 1. Create a GitHub Repository

If you haven't already, create a GitHub repository for hosting the web player:

```bash
# Create a new repo on GitHub at github.com/YourUsername/bbcradioplayer-web
# Then clone it:
git clone https://github.com/YourUsername/bbcradioplayer-web.git
cd bbcradioplayer-web
```

### 2. Copy Files

Copy the `index.html` file into your repository root:

```bash
cp index.html /path/to/bbcradioplayer-web/
```

### 3. Enable GitHub Pages

1. Go to your repository settings
2. Scroll to "GitHub Pages" section
3. Select "main" branch as the source
4. GitHub will provide you with a URL like: `https://yourusername.github.io/bbcradioplayer-web/`

### 4. Configure Your Domain (Optional but Recommended)

If you own `bbcradioplayer.app`, you can point it to GitHub Pages:

1. Add a `CNAME` file to your repository with the content:
   ```
   bbcradioplayer.app
   ```

2. Configure your domain's DNS settings to point to GitHub Pages:
   - Add these DNS records:
     ```
     185.199.108.153
     185.199.109.153
     185.199.110.153
     185.199.111.153
     ```

3. Wait for DNS propagation (5-15 minutes)

### 5. Update ShareUtil.kt

In your Android app, update the `WEB_BASE_URL` in `ShareUtil.kt`:

```kotlin
// Before:
private const val WEB_BASE_URL = "https://bbcradioplayer.app"

// After (if using GitHub Pages):
private const val WEB_BASE_URL = "https://yourusername.github.io/bbcradioplayer-web"
```

## How It Works

### Share Flow

1. **User shares a podcast/episode** from the app via the share button
2. **ShareUtil generates a link** (e.g., `https://bbcradioplayer.app/e/{episodeId}`)
3. **Recipient receives the link** via text/email/social media
4. **If recipient has the app**: Deep link opens it directly (e.g., `app://episode/{id}`)
5. **If recipient doesn't have the app**: Web player loads with episode details and audio player

### Web Player Features

- **Responsive Design**: Works on desktop, tablet, and mobile
- **Audio Player**: Built-in HTML5 player for listening
- **App Install Prompt**: Encourages users to install the app
- **Deep Linking**: If the app is installed, an "Open in App" button will trigger the deep link
- **Share Link Copy**: Users can copy the link to share further
- **Subscribe Options**: Links to RSS feed and podcast platforms

## Integrating Real Data

The current `index.html` has mock data. To integrate with real podcast data:

### Option 1: Fetch from Your Backend

Replace the API calls in the JavaScript:

```javascript
async loadContent(type, id) {
    try {
        const response = await fetch(`${CONFIG.API_BASE}/${type}/${id}`);
        const content = await response.json();
        this.currentContent = content;
        this.render();
    } catch (error) {
        this.showError('Failed to load', 'Could not fetch podcast information.');
    }
}
```

### Option 2: Use Podcast Index API

You can integrate with [Podcast Index](https://podcastindex.org/) for podcast data:

```javascript
async loadContent(type, id) {
    if (type === 'p') {
        // Fetch podcast from Podcast Index
        const response = await fetch(`https://api.podcastindex.org/api/1.0/podcasts/byid?id=${id}`);
        const data = await response.json();
        this.currentContent = {
            type: 'podcast',
            ...data.feed
        };
    }
    this.render();
}
```

### Option 3: Generate Static Pages

For better performance, you can generate static HTML files at build time using your podcast data:

```bash
# Your build script would generate:
# - /p/podcast-id-1.html
# - /e/episode-id-1.html
# etc.
```

## Customization

### Colors

Update the gradient colors in the CSS:

```css
background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
```

### Branding

- Replace the app name "BBC Radio Player" with your app name
- Update the emojis and icons to match your brand
- Modify the description text to match your app

### Links

- Update the Google Play Store link to your actual app listing
- Configure podcast platform search links (Spotify, Apple Podcasts, etc.)

## Deployment

### Automatic Deployment

Set up GitHub Actions to auto-deploy from your main branch:

Create `.github/workflows/deploy.yml`:

```yaml
name: Deploy to GitHub Pages

on:
  push:
    branches: [ main ]

jobs:
  build-and-deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      
      - name: Deploy to GitHub Pages
        uses: peaceiris/actions-gh-pages@v3
        with:
          github_token: ${{ secrets.GITHUB_TOKEN }}
          publish_dir: ./
```

### Manual Deployment

```bash
git add -A
git commit -m "Update podcast player"
git push origin main
```

Changes appear live at your GitHub Pages URL within seconds.

## Testing

### Test Deep Links Locally

1. Build and run your app with the updated `ShareUtil.kt`
2. Use `adb` to test deep links:

```bash
adb shell am start -W -a android.intent.action.VIEW -d "app://podcast/test-id" com.hyliankid14.bbcradioplayer
```

### Test Web Player

1. Open `index.html` locally
2. Navigate to: `file:///path/to/web-player/index.html#/p/test-id`
3. Or test online: `https://yourusername.github.io/bbcradioplayer-web/p/test-id`

## Analytics (Optional)

Add Google Analytics to track sharing engagement:

```html
<script async src="https://www.googletagmanager.com/gtag/js?id=GA_ID"></script>
<script>
  window.dataLayer = window.dataLayer || [];
  function gtag(){dataLayer.push(arguments);}
  gtag('js', new Date());
  gtag('config', 'GA_ID');
</script>
```

## Troubleshooting

### Deep Links Not Working

- Ensure your app has the correct intent filters in `AndroidManifest.xml`
- Check that `ShareUtil.parseShareLink()` is called in `MainActivity.onCreate()`
- Test with: `adb shell am start -W -a android.intent.action.VIEW -d "app://podcast/id" com.hyliankid14.bbcradioplayer`

### Audio Player Not Playing

- Verify the audio URL is correct and accessible
- Check CORS headers on your audio server
- Ensure the URL uses `https://` (required for modern browsers)

### GitHub Pages Not Updating

- Clear your browser cache
- Wait 1-2 minutes for deployment to complete
- Check GitHub Actions tab for build errors

## Support

For issues or questions, open an issue in your repository or contact the app developers.
