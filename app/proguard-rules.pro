# R8 keep rules for the TED Talks app.
#
# Intentionally empty: every dependency this app uses ships its own consumer
# rules — OkHttp, Coil, Media3, AndroidX/Compose, Navigation 3, and
# kotlinx-serialization (whose bundled rules keep the generated serializers for
# the @Serializable NavKeys TalksList/TalkDetail used in back-stack persistence).
# Verified by a minified release build that parses the live RSS feed, navigates,
# and restores the back stack across process death.
#
# Add app-specific rules here only if a future change introduces reflection,
# JNI, or another pattern R8 cannot see from the bytecode.
