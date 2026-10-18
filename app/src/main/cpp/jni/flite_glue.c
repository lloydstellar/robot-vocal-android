/*
 * Flite for Robot Vocal: renders a list of phones with durations and pitch
 * that we choose, so every phone starts exactly where the beat grid wants it.
 * The voice's own duration and intonation models are replaced by two small
 * functions that read our numbers.
 */
#include <string.h>
#include "flite.h"
#include "glue.h"

cst_voice *register_cmu_us_kal(const char *voxdir);

static cst_voice *kal = NULL;
static const float *plan_durations = NULL;  /* seconds per phone */
static const float *plan_f0 = NULL;         /* Hz per phone */
static int plan_count = 0;

/* Every segment ends where the sum of our durations says. */
static cst_utterance *rv_durations(cst_utterance *u)
{
    cst_item *s;
    float end = 0.0f;
    int i = 0;
    for (s = relation_head(utt_relation(u, "Segment")); s; s = item_next(s), i++)
    {
        end += (i < plan_count) ? plan_durations[i] : 0.05f;
        item_set_float(s, "end", end);
    }
    return u;
}

/* A pitch target at the start and the end of every segment: the pitch holds within a phone. */
static cst_utterance *rv_pitch(cst_utterance *u)
{
    cst_relation *targets = utt_relation_create(u, "Target");
    cst_item *s, *t;
    float start = 0.0f;
    int i = 0;
    for (s = relation_head(utt_relation(u, "Segment")); s; s = item_next(s), i++)
    {
        float end = item_feat_float(s, "end");
        float f0 = (i < plan_count) ? plan_f0[i] : 100.0f;
        t = relation_append(targets, NULL);
        item_set_float(t, "pos", start + 0.001f);
        item_set_float(t, "f0", f0);
        t = relation_append(targets, NULL);
        item_set_float(t, "pos", end - 0.001f);
        item_set_float(t, "f0", f0);
        start = end;
    }
    return u;
}

int rv_flite_init(void)
{
    if (kal == NULL)
    {
        flite_init();
        kal = register_cmu_us_kal(NULL);
        feat_set(kal->features, "duration_model_func", uttfunc_val(&rv_durations));
        feat_set(kal->features, "f0_model_func", uttfunc_val(&rv_pitch));
    }
    return kal != NULL ? get_param_int(kal->features, "sample_rate", 8000) : -1;
}

int rv_flite_render(const char *phones, const float *durations, const float *f0, int count, short **out)
{
    cst_utterance *u;
    cst_wave *w;
    int n;
    if (rv_flite_init() < 0) return -1;
    plan_durations = durations;
    plan_f0 = f0;
    plan_count = count;
    u = flite_synth_phones(phones, kal);
    plan_count = 0;
    if (u == NULL) return -1;
    w = utt_wave(u);
    n = w ? w->num_samples : 0;
    *out = NULL;
    if (n > 0)
    {
        *out = (short *) malloc(sizeof(short) * n);
        memcpy(*out, w->samples, sizeof(short) * n);
    }
    delete_utterance(u);
    return n;
}
