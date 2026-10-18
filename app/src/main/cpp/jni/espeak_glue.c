/*
 * eSpeak NG for Robot Vocal: synchronous synthesis into memory, with the
 * sample position of every phoneme, so the app can find each vowel onset.
 */
#include <stdlib.h>
#include <string.h>
#include <espeak-ng/speak_lib.h>
#include "glue.h"

static int rate = 0;
static short *samples = NULL;
static int sample_count = 0, sample_capacity = 0;
static char events[16384];
static int events_length = 0;

static int collect(short *wav, int count, espeak_EVENT *e)
{
    if (wav != NULL && count > 0)
    {
        if (sample_count + count > sample_capacity)
        {
            int capacity = (sample_count + count) * 2;
            short *grown = (short *) realloc(samples, sizeof(short) * capacity);
            if (grown == NULL) return 1;
            samples = grown;
            sample_capacity = capacity;
        }
        memcpy(samples + sample_count, wav, sizeof(short) * count);
        sample_count += count;
    }
    for (; e != NULL && e->type != espeakEVENT_LIST_TERMINATED; e++)
    {
        if (e->type == espeakEVENT_PHONEME && events_length < (int) sizeof(events) - 32)
        {
            char name[9];
            memcpy(name, e->id.string, 8);
            name[8] = 0;
            events_length += snprintf(events + events_length, sizeof(events) - events_length, "%s %d\n", name, e->sample);
        }
    }
    return 0;
}

int rv_espeak_init(const char *data_path)
{
    if (rate <= 0)
    {
        rate = espeak_Initialize(AUDIO_OUTPUT_SYNCHRONOUS, 0, data_path,
                                 espeakINITIALIZE_PHONEME_EVENTS | espeakINITIALIZE_DONT_EXIT);
        if (rate > 0) espeak_SetSynthCallback(collect);
    }
    return rate;
}

int rv_espeak_render(const char *voice, const char *text, int speed, int pitch, int range, short **out, const char **phoneme_events)
{
    if (rate <= 0) return -1;
    if (espeak_SetVoiceByName(voice) != EE_OK) return -2;
    espeak_SetParameter(espeakRATE, speed, 0);
    espeak_SetParameter(espeakPITCH, pitch, 0);
    espeak_SetParameter(espeakRANGE, range, 0);
    espeak_SetParameter(espeakVOLUME, 100, 0);
    sample_count = 0;
    events_length = 0;
    events[0] = 0;
    if (espeak_Synth(text, strlen(text) + 1, 0, POS_CHARACTER, 0, espeakCHARS_UTF8 | espeakPHONEMES, NULL, NULL) != EE_OK) return -3;
    espeak_Synchronize();
    *out = samples;
    *phoneme_events = events;
    return sample_count;
}
