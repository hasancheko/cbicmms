"use client";

import { Button, ButtonProps } from "@mui/material";
import { useLocale } from "next-intl";
import { getSignupUrl } from "src/utils/urlPaths";

interface SignupButtonProps extends ButtonProps {
  params?: Record<string, string>;
}

export default function SignupButton({ params, ...props }: SignupButtonProps) {
  const locale = useLocale();
  const serverSignupUrl = getSignupUrl(locale, params);

  return <Button component="a" variant={"contained"} href={serverSignupUrl} data-utm-enriched {...props} />;
}
