# The native part of Robot Vocal: the speech engines Flite and eSpeak NG, and
# the JNI functions the app calls. Both engines are built from the sources in
# this folder, with their own licence files next to them.
cmake_minimum_required(VERSION 3.22.1)
project(robotvocal C)

# Flite: the core, the English phone set and lexicon, the kal diphone voice.
file(GLOB FLITE_SOURCES
    flite/src/hrg/*.c flite/src/lexicon/*.c flite/src/regex/*.c flite/src/speech/*.c
    flite/src/stats/*.c flite/src/synth/*.c flite/src/utils/*.c flite/src/wavesynth/*.c flite/src/cg/*.c
    flite/src/audio/audio.c flite/src/audio/au_none.c flite/src/audio/au_streaming.c
    flite/lang/usenglish/*.c flite/lang/cmu_us_kal/*.c)
# the other cmulex files are tables included by these
list(APPEND FLITE_SOURCES
    flite/lang/cmulex/cmu_lts_rules.c flite/lang/cmulex/cmu_lts_model.c flite/lang/cmulex/cmu_lex.c
    flite/lang/cmulex/cmu_lex_entries.c flite/lang/cmulex/cmu_lex_data.c flite/lang/cmulex/cmu_postlex.c)
add_library(flite STATIC ${FLITE_SOURCES})
target_include_directories(flite PUBLIC flite/include PRIVATE flite/lang/usenglish flite/lang/cmulex)
target_compile_definitions(flite PRIVATE CST_AUDIO_NONE CST_NO_SOCKETS)
target_compile_options(flite PRIVATE -O2 -w)

# eSpeak NG: the library with the Klatt voices, synchronous output, no audio device.
file(GLOB ESPEAK_SOURCES espeak-ng/libespeak-ng/*.c espeak-ng/ucd-tools/src/*.c)
add_library(espeak-ng STATIC ${ESPEAK_SOURCES})
target_include_directories(espeak-ng
    PUBLIC espeak-ng/include
    PRIVATE espeak-ng/libespeak-ng espeak-ng/include/espeak-ng espeak-ng/ucd-tools/src/include)
target_compile_definitions(espeak-ng PRIVATE LIBESPEAK_NG_EXPORT=1 "PATH_ESPEAK_DATA=\"\"")
target_compile_options(espeak-ng PRIVATE -O2 -w -fno-exceptions -fwrapv)

add_library(robotvocal SHARED jni/robotvocal_jni.c jni/flite_glue.c jni/espeak_glue.c)
target_link_libraries(robotvocal PRIVATE flite espeak-ng m log)
