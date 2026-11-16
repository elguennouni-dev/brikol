package ma.sanai3i.service;

import ma.sanai3i.domain.Setting;
import ma.sanai3i.repo.SettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Platform switches the admin can change without redeploying. */
@Service
@Transactional
public class SettingsService {
    public static final String REGISTRATIONS_OPEN = "registrationsOpen";
    public static final String BANNER = "bannerMessage";

    private final SettingRepository repo;

    public SettingsService(SettingRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public boolean registrationsOpen() {
        return !"false".equalsIgnoreCase(get(REGISTRATIONS_OPEN, "true"));
    }

    @Transactional(readOnly = true)
    public String banner() {
        return get(BANNER, "");
    }

    public void update(boolean registrationsOpen, String banner) {
        put(REGISTRATIONS_OPEN, String.valueOf(registrationsOpen));
        put(BANNER, banner == null ? "" : banner.trim());
    }

    private String get(String key, String fallback) {
        return repo.findById(key).map(s -> s.value).orElse(fallback);
    }

    private void put(String key, String value) {
        Setting s = repo.findById(key).orElseGet(() -> {
            Setting n = new Setting();
            n.key = key;
            return n;
        });
        s.value = value;
        repo.save(s);
    }
}
