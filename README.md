# AAPS
* Check the wiki: https://wiki.aaps.app
*  Everyone who’s been looping with AAPS needs to fill out the form after 3 days of looping  https://docs.google.com/forms/d/14KcMjlINPMJHVt28MDRupa4sz4DDIooI4SrW0P3HSN8/viewform?c=0&w=1

[![Support Server](https://img.shields.io/discord/629952586895851530.svg?label=Discord&logo=Discord&colorB=7289da&style=for-the-badge)](https://discord.gg/4fQUWHZ4Mw)

[![CircleCI](https://circleci.com/gh/nightscout/AndroidAPS/tree/master.svg?style=svg)](https://circleci.com/gh/nightscout/AndroidAPS/tree/master)
[![Crowdin](https://d322cqt584bo4o.cloudfront.net/androidaps/localized.svg)](https://translations.aaps.app/project/androidaps)
[![Documentation Status](https://readthedocs.org/projects/androidaps/badge/?version=latest)](https://wiki.aaps.app/en/latest/?badge=latest)
[![codecov](https://codecov.io/gh/nightscout/AndroidAPS/branch/master/graph/badge.svg?token=EmklfIV6bH)](https://codecov.io/gh/nightscout/AndroidAPS)

DEV: 
[![CircleCI](https://circleci.com/gh/nightscout/AndroidAPS/tree/dev.svg?style=svg)](https://circleci.com/gh/nightscout/AndroidAPS/tree/dev)
[![codecov](https://codecov.io/gh/nightscout/AndroidAPS/branch/dev/graph/badge.svg?token=EmklfIV6bH)](https://codecov.io/gh/nightscout/AndroidAPS/tree/dev)

<img src="https://cdn.iconscout.com/icon/free/png-256/bitcoin-384-920569.png" srcset="https://cdn.iconscout.com/icon/free/png-512/bitcoin-384-920569.png 2x" alt="Bitcoin Icon" width="100">

3KawK8aQe48478s6fxJ8Ms6VTWkwjgr9f2


### git update
- git submodule update --init --recursive



git:[rebelning/dev]
git merge master
Auto-merging app/build.gradle.kts
CONFLICT (content): Merge conflict in app/build.gradle.kts
Auto-merging app/src/main/AndroidManifest.xml
Auto-merging app/src/main/kotlin/app/aaps/MainActivity.kt
Auto-merging app/src/main/kotlin/app/aaps/MainApp.kt
CONFLICT (content): Merge conflict in app/src/main/kotlin/app/aaps/MainApp.kt
Auto-merging app/src/main/kotlin/app/aaps/activities/MyPreferenceFragment.kt
CONFLICT (content): Merge conflict in app/src/main/kotlin/app/aaps/activities/MyPreferenceFragment.kt
Auto-merging app/src/main/kotlin/app/aaps/di/AppComponent.kt
CONFLICT (content): Merge conflict in app/src/main/kotlin/app/aaps/di/AppComponent.kt
Auto-merging app/src/main/kotlin/app/aaps/di/AppModule.kt
CONFLICT (content): Merge conflict in app/src/main/kotlin/app/aaps/di/AppModule.kt
Auto-merging app/src/main/kotlin/app/aaps/di/PluginsListModule.kt
CONFLICT (content): Merge conflict in app/src/main/kotlin/app/aaps/di/PluginsListModule.kt
Auto-merging app/src/main/kotlin/app/aaps/implementations/UiInteractionImpl.kt
Auto-merging app/src/main/kotlin/app/aaps/receivers/DataReceiver.kt
CONFLICT (content): Merge conflict in app/src/main/kotlin/app/aaps/receivers/DataReceiver.kt
Auto-merging buildSrc/src/main/kotlin/Versions.kt
CONFLICT (content): Merge conflict in buildSrc/src/main/kotlin/Versions.kt
Auto-merging core/data/src/main/kotlin/app/aaps/core/data/pump/defs/PumpType.kt
Auto-merging core/interfaces/src/main/kotlin/app/aaps/core/interfaces/maintenance/ImportExportPrefs.kt
CONFLICT (content): Merge conflict in core/interfaces/src/main/kotlin/app/aaps/core/interfaces/maintenance/ImportExportPrefs.kt
CONFLICT (modify/delete): core/interfaces/src/main/kotlin/app/aaps/core/interfaces/maintenance/PrefFileListProvider.kt deleted in master and modified in HEAD.  Version HEAD of core/interfaces/src/main/kotlin/app/aaps/core/interfaces/maintenance/PrefFileListProvider.kt left in tree.
Auto-merging core/interfaces/src/main/kotlin/app/aaps/core/interfaces/pump/Pump.kt
CONFLICT (modify/delete): core/interfaces/src/main/kotlin/app/aaps/core/interfaces/pump/defs/PumpCapability.kt deleted in master and modified in HEAD.  Version HEAD of core/interfaces/src/main/kotlin/app/aaps/core/interfaces/pump/defs/PumpCapability.kt left in tree.
Auto-merging core/interfaces/src/main/kotlin/app/aaps/core/interfaces/receivers/Intents.kt
Auto-merging core/interfaces/src/main/kotlin/app/aaps/core/interfaces/ui/UiInteraction.kt
Auto-merging core/ui/src/main/res/values-uk-rUA/strings.xml
Auto-merging core/ui/src/main/res/values-zh-rCN/strings.xml
Auto-merging core/ui/src/main/res/values/colors.xml
Auto-merging core/ui/src/main/res/values/strings.xml
CONFLICT (content): Merge conflict in core/ui/src/main/res/values/strings.xml
Auto-merging core/utils/src/main/res/values/keys.xml
CONFLICT (content): Merge conflict in core/utils/src/main/res/values/keys.xml
CONFLICT (modify/delete): database/entities/src/main/kotlin/app/aaps/database/entities/GlucoseValue.kt deleted in master and modified in HEAD.  Version HEAD of database/entities/src/main/kotlin/app/aaps/database/entities/GlucoseValue.kt left in tree.
CONFLICT (modify/delete): database/entities/src/main/kotlin/app/aaps/database/entities/UserEntry.kt deleted in master and modified in HEAD.  Version HEAD of database/entities/src/main/kotlin/app/aaps/database/entities/UserEntry.kt left in tree.
Auto-merging database/impl/src/main/kotlin/app/aaps/database/entities/embedments/InterfaceIDs.kt
Auto-merging database/persistence/src/main/kotlin/app/aaps/database/persistence/converters/PumpTypeExtension.kt
CONFLICT (content): Merge conflict in database/persistence/src/main/kotlin/app/aaps/database/persistence/converters/PumpTypeExtension.kt
Auto-merging implementation/src/main/kotlin/app/aaps/implementation/queue/commands/CommandLoadEvents.kt
Auto-merging implementation/src/main/kotlin/app/aaps/implementation/queue/commands/CommandLoadHistory.kt
Auto-merging implementation/src/main/kotlin/app/aaps/implementation/queue/commands/CommandSetUserSettings.kt
Auto-merging implementation/src/main/kotlin/app/aaps/implementation/userEntry/UserEntryPresentationHelperImpl.kt
CONFLICT (modify/delete): insight/src/main/java/info/nightscout/androidaps/plugins/pump/insight/LocalInsightPlugin.java deleted in master and modified in HEAD.  Version HEAD of insight/src/main/java/info/nightscout/androidaps/plugins/pump/insight/LocalInsightPlugin.java left in tree.
Auto-merging plugins/configuration/src/main/kotlin/app/aaps/plugins/configuration/maintenance/FileListProviderImpl.kt
Auto-merging plugins/configuration/src/main/kotlin/app/aaps/plugins/configuration/maintenance/ImportExportPrefsImpl.kt
Auto-merging plugins/configuration/src/main/res/values-zh-rCN/strings.xml
Auto-merging plugins/configuration/src/main/res/values/strings.xml
Auto-merging plugins/constraints/src/main/kotlin/app/aaps/plugins/constraints/versionChecker/VersionCheckerPlugin.kt
Auto-merging plugins/main/build.gradle.kts
CONFLICT (content): Merge conflict in plugins/main/build.gradle.kts
Auto-merging plugins/main/src/main/AndroidManifest.xml
Auto-merging plugins/main/src/main/kotlin/app/aaps/plugins/main/general/overview/OverviewFragment.kt
CONFLICT (content): Merge conflict in plugins/main/src/main/kotlin/app/aaps/plugins/main/general/overview/OverviewFragment.kt
Auto-merging plugins/main/src/main/kotlin/app/aaps/plugins/main/general/overview/ui/StatusLightHandler.kt
CONFLICT (content): Merge conflict in plugins/main/src/main/kotlin/app/aaps/plugins/main/general/overview/ui/StatusLightHandler.kt
Auto-merging plugins/main/src/main/res/values-zh-rCN/strings.xml
Auto-merging plugins/main/src/main/res/values/strings.xml
Auto-merging plugins/source/src/main/kotlin/app/aaps/plugins/source/BGSourceFragment.kt
CONFLICT (content): Merge conflict in plugins/source/src/main/kotlin/app/aaps/plugins/source/BGSourceFragment.kt
Auto-merging plugins/source/src/main/kotlin/app/aaps/plugins/source/di/SourceModule.kt
CONFLICT (content): Merge conflict in plugins/source/src/main/kotlin/app/aaps/plugins/source/di/SourceModule.kt
Auto-merging plugins/source/src/main/res/values-zh-rCN/strings.xml
CONFLICT (content): Merge conflict in plugins/source/src/main/res/values-zh-rCN/strings.xml
Auto-merging plugins/source/src/main/res/values/strings.xml
CONFLICT (content): Merge conflict in plugins/source/src/main/res/values/strings.xml
Auto-merging plugins/sync/src/main/AndroidManifest.xml
Auto-merging plugins/sync/src/main/kotlin/app/aaps/plugins/sync/di/SyncModule.kt
Auto-merging plugins/sync/src/main/kotlin/app/aaps/plugins/sync/nsclient/services/NSClientService.kt
Auto-merging plugins/sync/src/main/kotlin/app/aaps/plugins/sync/nsclientV3/services/NSClientV3Service.kt
Auto-merging plugins/sync/src/main/res/values-zh-rCN/strings.xml
CONFLICT (content): Merge conflict in plugins/sync/src/main/res/values-zh-rCN/strings.xml
Auto-merging plugins/sync/src/main/res/values/strings.xml
CONFLICT (content): Merge conflict in plugins/sync/src/main/res/values/strings.xml
CONFLICT (modify/delete): pump/combo/src/main/java/info/nightscout/pump/combo/ComboPlugin.java deleted in master and modified in HEAD.  Version HEAD of pump/combo/src/main/java/info/nightscout/pump/combo/ComboPlugin.java left in tree.
Auto-merging pump/combov2/src/main/kotlin/info/nightscout/pump/combov2/ComboV2Plugin.kt
CONFLICT (content): Merge conflict in pump/combov2/src/main/kotlin/info/nightscout/pump/combov2/ComboV2Plugin.kt
CONFLICT (modify/delete): pump/danar/src/main/java/info/nightscout/androidaps/danaRv2/DanaRv2Plugin.java deleted in master and modified in HEAD.  Version HEAD of pump/danar/src/main/java/info/nightscout/androidaps/danaRv2/DanaRv2Plugin.java left in tree.
CONFLICT (modify/delete): pump/danar/src/main/java/info/nightscout/androidaps/danar/AbstractDanaRPlugin.java deleted in master and modified in HEAD.  Version HEAD of pump/danar/src/main/java/info/nightscout/androidaps/danar/AbstractDanaRPlugin.java left in tree.
CONFLICT (modify/delete): pump/danar/src/main/java/info/nightscout/androidaps/danar/DanaRPlugin.java deleted in master and modified in HEAD.  Version HEAD of pump/danar/src/main/java/info/nightscout/androidaps/danar/DanaRPlugin.java left in tree.
Auto-merging pump/danar/src/main/kotlin/app/aaps/pump/danarkorean/DanaRKoreanPlugin.kt
Auto-merging pump/danars/src/main/kotlin/app/aaps/pump/danars/DanaRSPlugin.kt
Auto-merging pump/diaconn/src/main/kotlin/app/aaps/pump/diaconn/DiaconnG8Plugin.kt
Auto-merging pump/eopatch/src/main/kotlin/app/aaps/pump/eopatch/EopatchPumpPlugin.kt
Auto-merging pump/medtronic/src/main/kotlin/app/aaps/pump/medtronic/MedtronicPumpPlugin.kt
Auto-merging pump/medtrum/src/main/kotlin/app/aaps/pump/medtrum/MedtrumPlugin.kt
Auto-merging pump/omnipod-dash/src/main/kotlin/app/aaps/pump/omnipod/dash/OmnipodDashPumpPlugin.kt
Auto-merging pump/omnipod-eros/src/main/java/app/aaps/pump/omnipod/eros/OmnipodErosPumpPlugin.java
Auto-merging pump/pump-common/src/main/kotlin/app/aaps/pump/common/PumpPluginAbstract.kt
Auto-merging pump/virtual/src/main/kotlin/app/aaps/pump/virtual/VirtualPumpPlugin.kt
Auto-merging settings.gradle
CONFLICT (content): Merge conflict in settings.gradle
Auto-merging shared/impl/src/main/kotlin/app/aaps/shared/impl/di/SharedImplModule.kt
Auto-merging ui/src/main/kotlin/app/aaps/ui/dialogs/BolusProgressDialog.kt
Auto-merging ui/src/main/res/values-zh-rCN/strings.xml
Auto-merging ui/src/main/res/values/strings.xml
CONFLICT (content): Merge conflict in ui/src/main/res/values/strings.xml
Automatic merge failed; fix conflicts and then commit the result.
