/*
 * md4a_events.h — stream md4c parse events into a compact binary event
 * buffer (JNI-free; unit-testable standalone).
 *
 * The Kotlin side (Md4aEventDecoder) turns the buffer into the MdBlock AST.
 * Format: stream of opcodes (u8 LE) + payloads; strings are u32 len + UTF-8.
 */
#ifndef MD4A_EVENTS_H
#define MD4A_EVENTS_H

#include <stddef.h>
#include <stdint.h>

/* Block events */
#define EV_QUOTE_BEGIN   0x01
#define EV_QUOTE_END     0x02
#define EV_LIST_BEGIN    0x03  /* u8 ordered, u32 start */
#define EV_LIST_END      0x04
#define EV_LI_BEGIN      0x05  /* u8 is_task, u8 checked */
#define EV_LI_END        0x06
#define EV_H_BEGIN       0x07  /* u8 level */
#define EV_H_END         0x08
#define EV_P_BEGIN       0x09
#define EV_P_END         0x0A
#define EV_CODE_BEGIN    0x0B  /* str lang (may be empty) */
#define EV_CODE_END      0x0C
#define EV_HTML_BEGIN    0x0D  /* raw HTML block: HTML text between BEGIN/END */
#define EV_HTML_END      0x0E
#define EV_TABLE_BEGIN   0x0F
#define EV_TABLE_END     0x10
#define EV_THEAD_BEGIN   0x11
#define EV_THEAD_END     0x12
#define EV_TBODY_BEGIN   0x13
#define EV_TBODY_END     0x14
#define EV_TR_BEGIN      0x15
#define EV_TR_END        0x16
#define EV_TD_BEGIN      0x17  /* u8 align (0=L 1=C 2=R); head/body tracked via THEAD/TBODY */
#define EV_TD_END        0x18
#define EV_HR            0x19

/* Inline events */
#define EV_TEXT          0x20  /* str */
#define EV_ENTITY        0x21  /* str, e.g. "&amp;" — decoded on the Kotlin side */
#define EV_SOFTBR        0x22
#define EV_BR            0x23  /* hard line break */
#define EV_EM_BEGIN      0x24  /* u8 strong */
#define EV_EM_END        0x25
#define EV_DEL_BEGIN     0x26
#define EV_DEL_END       0x27
#define EV_A_BEGIN       0x28  /* str url, str-or-null title */
#define EV_A_END         0x29
#define EV_IMG_BEGIN     0x2A  /* str src, str-or-null title */
#define EV_IMG_END       0x2B
#define EV_CODESPAN_BEGIN 0x2C
#define EV_CODESPAN_END  0x2D
#define EV_HTML_INLINE   0x2E /* str — raw inline HTML chunk (<a href=…>, <img …>, …) */

typedef struct md4a_buf {
    uint8_t *data;
    size_t len;
    size_t cap;
} md4a_buf;

int md4a_buf_init(md4a_buf *b);
void md4a_buf_free(md4a_buf *b);
int md4a_buf_u8(md4a_buf *b, uint8_t v);
int md4a_buf_u32(md4a_buf *b, uint32_t v);
int md4a_buf_str(md4a_buf *b, const char *s, size_t n);        /* length-prefixed, may be NULL (absent) */
int md4a_buf_opt_str(md4a_buf *b, const char *s, size_t n);    /* 0x01 + str, or 0x00 for absent */

/* Parse `input` (md4c, `flags` = MD_FLAG_* bitmask) and append the event
 * stream to `b`. Returns 0 on success, -1 on OOM/abort. */
int md4a_parse_to_events(unsigned flags, const char *input, size_t size, md4a_buf *b);

#endif
