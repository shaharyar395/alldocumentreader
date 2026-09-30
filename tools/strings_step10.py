"""Step 10 base (English) strings. HTML strings are stored entity-escaped so getString() returns raw HTML."""
PLAIN = {
 "theme_system": "System default", "theme_light": "Light", "theme_dark": "Dark",
 "file_manager": "File manager", "section_general": "General", "app_theme": "App theme",
 "feedback_or_suggestion": "Feedback or suggestion", "add_widget": "Add widget",
 "explore_more_apps": "Explore more apps", "terms_of_use": "Terms of use", "privacy_policy": "Privacy policy",
 "manage_subscriptions": "Manage subscriptions", "go_to_google_play": "Go to Google Play",
 "ask_every_time": "Ask every time",
 "share_app_text": "I use %1$s to open and edit PDF, Word, Excel and PowerPoint files. Try it:",
 "remove_ads": "Remove ads", "unlock_premium": "Unlock all premium features",
 "faq_view_edit": "View &amp; edit", "faq_manage": "Manage files", "faq_about_app": "About app",
 "faq_yes": "🥳 Yes", "faq_no": "😞 No", "faq_thanks": "Thanks for your feedback!",
 "faq_q_edit": "How can I edit a PDF?",
 "faq_q_display": "How can I change how a document is displayed?",
 "faq_q_copy": "How can I copy text?",
 "faq_q_search": "How can I search for text in a file?",
 "faq_q_open": "Why can't I find or open my file?",
 "faq_q_create": "How can I create a new file?",
 "faq_q_print": "How can I print a file?",
 "faq_q_subscription": "How do I manage or cancel my subscription?",
 "faq_q_ads": "Why are there ads in the app?",
 "faq_q_slow": "Why does a file open slowly?",
 "faq_q_permissions": "Why does the app need file access permission?",
 "rate_our_app": "Rate our app", "what_problems": "What problems did you meet?",
 "feedback_hint": "Please describe the problem or your suggestion in detail…",
 "add_screenshot": "Add screenshot", "submit": "Submit",
 "fb_cant_open": "Can't open file", "fb_too_many_ads": "Too many ads", "fb_slow": "Slow",
 "fb_crashes": "Crashes", "fb_others": "Others", "fb_rating_line": "Rating: %1$d/5",
 "fb_max_shots": "You can add up to 4 screenshots",
 "get_premium": "Get Premium", "premium_tagline": "Enjoy all features without ads",
 "premium_slide_sign": "Sign documents anywhere", "premium_slide_convert": "Convert files in one tap",
 "premium_slide_templates": "Unlimited templates", "premium_slide_pages": "Manage pages freely",
 "feat_ad_free": "100% ad-free experience", "feat_edit_sign": "Edit &amp; sign PDF",
 "feat_convert": "Convert Word, images and PDF", "feat_templates": "All document templates",
 "feat_pages": "Merge, split and organize pages",
 "plan_trial": "3-day free trial", "plan_then_yearly": "Then billed yearly", "plan_yearly": "Yearly",
 "plan_best_value": "Best value", "plan_monthly": "Monthly", "plan_price_on_play": "Price shown on Google Play",
 "continue_label": "Continue", "restore": "Restore", "no_subscription_found": "No subscription found",
 "billing_not_ready": "Subscriptions are not available in this version yet",
 "w_home": "Home", "w_recent": "Recent", "w_tools_title": "Quick tools",
 "w_tools_desc": "Search, open recent files and bookmarks, or edit a PDF from your home screen",
 "w_reader_title": "Document reader", "w_reader_desc": "Open PDF, Word, Excel and PowerPoint files in one tap",
 "w_editor_title": "PDF editor", "w_editor_desc": "Jump straight into editing a PDF",
 "add_widget_button": "Add to home screen",
 "widget_manual_hint": "Long-press your home screen, tap Widgets and choose this app",
 "thanks_support": "Thanks for your support!", "dont_miss_apps": "Don't miss our other useful apps",
 "see_our_apps": "See our apps on Google Play",
}
HTML = {
 "faq_a_edit": "Open the <b>Tools</b> tab and tap <b>Edit PDF</b>, then pick a file. You can edit existing text, add new text, add images, annotate (highlight, underline, strike-through, draw) and add your signature. Tap the check mark to save a copy.",
 "faq_a_display": "While reading, use the bottom bar to switch between <b>vertical</b> and <b>horizontal</b> scrolling, rotate the view or invert colors. Pinch to zoom, and tap the page number to jump to a page.",
 "faq_a_copy": "Copying text isn't available yet. We're working on it! If this matters to you, <a href=\"app://feedback\">send us feedback</a> so we can prioritise it.",
 "faq_a_search": "Open the document and tap <b>Search</b> in the bottom bar, then type the word you're looking for. Use the arrows to move between results. To find a file by name, use the search icon at the top of the home screen.",
 "faq_a_open": "Make sure the app has permission to access your files. If the file is in a cloud app or another folder, open it with your <a href=\"app://filemanager\">file manager</a> and choose this app. If a file still won't open it may be damaged or password-protected; please <a href=\"app://feedback\">tell us</a>.",
 "faq_a_create": "On the <b>All files</b> page tap the <b>+</b> button. You can create a document from a template, import files, convert images to PDF or scan paper documents with your camera.",
 "faq_a_print": "Open a PDF, tap the <b>⋮</b> menu and choose <b>Print</b>. Other formats can first be converted to PDF from the <b>Tools</b> tab.",
 "faq_a_subscription": "Subscriptions are managed by Google Play. Go to <b>Settings → Manage subscriptions</b>, or open Google Play → Profile → Payments &amp; subscriptions. See our <a href=\"app://terms\">Terms of use</a> for details.",
 "faq_a_ads": "Ads help us keep the app free for everyone. You can remove them with <a href=\"app://premium\">Premium</a>.",
 "faq_a_slow": "Very large files or files with many images take longer to load. Closing other apps and freeing storage space can help. If a file is always slow, please <a href=\"app://feedback\">send us feedback</a>.",
 "faq_a_permissions": "The app needs file access only to find and open the documents on your device. Your files stay on your device and are never uploaded.",
 "premium_legal": "The subscription renews automatically unless cancelled at least 24 hours before the end of the current period. You can cancel any time in Google Play. By continuing you agree to our <a href=\"app://terms\">Terms of use</a>.",
 "terms_html": "<p><i>Last updated: September 2026</i></p><p>These Terms of use govern your use of <b>%1$s</b> (the \"App\"). By using the App you agree to these terms.</p><h4>1. Use of the App</h4><p>You may use the App to view, create, convert and edit documents for personal or business purposes. You are responsible for the documents you open and create, and for having the right to use them.</p><h4>2. Subscriptions</h4><p>Some features may require a paid subscription purchased through Google Play. Subscriptions renew automatically unless cancelled at least 24 hours before the end of the current period. You can manage or cancel a subscription in your Google Play account settings. Refunds follow Google Play policies.</p><h4>3. Advertising</h4><p>The free version of the App may display ads provided by third parties.</p><h4>4. No warranty</h4><p>The App is provided \"as is\". We do our best to keep it reliable, but we do not guarantee that it will be error-free, and we are not liable for loss of data. Please keep backups of important files.</p><h4>5. Changes</h4><p>We may update these terms from time to time. Continued use of the App means you accept the updated terms.</p><h4>6. Contact</h4><p>Questions? Email us at %2$s.</p>",
 "privacy_html": "<p><i>Last updated: September 2026</i></p><p>This policy explains how <b>%1$s</b> (the \"App\") handles your information.</p><h4>1. Your documents</h4><p>Documents are opened, converted and edited on your device. We do not upload, read or store your files on our servers.</p><h4>2. Permissions</h4><p>File access is used only to list and open documents. Camera access is used only when you scan or photograph a document.</p><h4>3. Information collected by third parties</h4><p>If the App shows ads or uses Google Play services, those providers may collect device identifiers and usage data according to their own privacy policies.</p><h4>4. Feedback</h4><p>When you send feedback, we receive the email address you send from and the content you choose to include, and use them only to respond and improve the App.</p><h4>5. Children</h4><p>The App is not directed at children under 13.</p><h4>6. Contact</h4><p>For privacy questions, email %2$s.</p>",
}
def esc(v, html):
    v = v.replace("'", "\\'")
    if html:
        v = v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace('"', '\\"')
    return v
if __name__ == "__main__":
    p = "app/src/main/res/values/strings.xml"
    s = open(p, encoding="utf-8").read()
    add = "\n    <!-- Step 10: settings -->\n"
    for k, v in PLAIN.items():
        if f'name="{k}"' in s: continue
        fmt = ' formatted="false"' if False else ""
        add += f'    <string name="{k}">{esc(v, False)}</string>\n'
    for k, v in HTML.items():
        if f'name="{k}"' in s: continue
        add += f'    <string name="{k}">{esc(v, True)}</string>\n'
    s = s.replace("</resources>", add + "</resources>")
    open(p, "w", encoding="utf-8").write(s)
    print("ok")
