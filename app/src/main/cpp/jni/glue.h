#ifndef ROBOTVOCAL_GLUE_H
#define ROBOTVOCAL_GLUE_H

#include <stdio.h>
#include <stdlib.h>

int rv_flite_init(void);
/* Renders phones such as "pau r ow b aa t pau"; the caller frees *out. Returns the number of samples. */
int rv_flite_render(const char *phones, const float *durations, const float *f0, int count, short **out);

int rv_espeak_init(const char *data_path);
/* Renders text (phonemes in [[ ]]); *out stays owned by the glue. Returns the number of samples. */
int rv_espeak_render(const char *voice, const char *text, int speed, int pitch, int range, short **out, const char **phoneme_events);

#endif
